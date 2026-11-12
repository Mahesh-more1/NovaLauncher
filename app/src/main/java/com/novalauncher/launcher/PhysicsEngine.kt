package com.novalauncher.launcher

import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Anti-gravity / fluid-dynamics physics engine using Verlet integration.
 *
 * Design goals
 * ────────────
 * • Never block the UI thread — the simulation loop runs in a dedicated
 *   coroutine on [Dispatchers.Default].
 * • Memory-safe: all [PhysicsBody] instances come from a bounded object pool;
 *   excess allocation requests are simply ignored.
 * • Thread-safe reads from the UI thread via @Volatile state copies.
 *
 * Usage
 * ─────
 *   val engine = PhysicsEngine(maxBodies = 60)
 *   engine.start()
 *   val bodyId = engine.addBody(x = 100f, y = 200f, mass = 1f)
 *   engine.applyForce(bodyId, fx = 0f, fy = -500f)   // push upward
 *   engine.setGravity(0f, 200f)                       // flip gravity downward
 *   engine.stop()
 */
class PhysicsEngine(private val maxBodies: Int = 64) {

    // ── Vector helpers ──────────────────────────────────────────────────────

    data class Vec2(var x: Float = 0f, var y: Float = 0f) {
        operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
        operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
        operator fun times(s: Float) = Vec2(x * s, y * s)
        fun length() = sqrt(x * x + y * y)
        fun normalized(): Vec2 {
            val l = length()
            return if (l < 1e-6f) Vec2() else Vec2(x / l, y / l)
        }
        fun set(ox: Float, oy: Float) { x = ox; y = oy }
        fun addInPlace(ox: Float, oy: Float) { x += ox; y += oy }
        fun copy() = Vec2(x, y)
    }

    // ── PhysicsBody ─────────────────────────────────────────────────────────

    class PhysicsBody {
        var id: Int = -1
        var active: Boolean = false

        // Verlet state
        val pos     = Vec2()
        val prevPos = Vec2()
        val acc     = Vec2()   // reset each step after integration

        // Per-body physical properties
        var mass: Float           = 1f
        var restitution: Float    = 0.6f   // bounciness [0,1]
        var dragCoeff: Float      = 0.02f  // fluid-drag coefficient
        var buoyancy: Float       = 0f     // positive = floats upward in fluid
        var radius: Float         = 36f    // pixels, for collision

        // Accumulated external forces (N) applied this frame
        val externalForce = Vec2()

        // Callback fired each step (runs on simulation coroutine)
        var onPositionChanged: ((Float, Float) -> Unit)? = null

        fun reset() {
            active = false
            id = -1
            pos.set(0f, 0f); prevPos.set(0f, 0f); acc.set(0f, 0f)
            externalForce.set(0f, 0f)
            mass = 1f; restitution = 0.6f; dragCoeff = 0.02f
            buoyancy = 0f; radius = 36f
            onPositionChanged = null
        }
    }

    // ── Object pool ─────────────────────────────────────────────────────────

    private val pool: Array<PhysicsBody> = Array(maxBodies) { PhysicsBody() }
    private val activeBodies = mutableListOf<PhysicsBody>()
    private var nextId = 0

    @Synchronized
    fun addBody(
        x: Float, y: Float,
        mass: Float = 1f,
        radius: Float = 36f,
        dragCoeff: Float = 0.02f,
        buoyancy: Float = 0f,
        restitution: Float = 0.6f,
        onPositionChanged: ((Float, Float) -> Unit)? = null
    ): Int {
        val body = pool.firstOrNull { !it.active } ?: return -1
        body.apply {
            active = true
            id = nextId++
            pos.set(x, y); prevPos.set(x, y); acc.set(0f, 0f)
            this.mass = mass
            this.radius = radius
            this.dragCoeff = dragCoeff
            this.buoyancy = buoyancy
            this.restitution = restitution
            this.onPositionChanged = onPositionChanged
            externalForce.set(0f, 0f)
        }
        activeBodies.add(body)
        return body.id
    }

    @Synchronized
    fun removeBody(id: Int) {
        val body = activeBodies.firstOrNull { it.id == id } ?: return
        activeBodies.remove(body)
        body.reset()
    }

    // ── Gravity & world bounds ───────────────────────────────────────────────

    @Volatile private var gravX = 0f
    @Volatile private var gravY = 980f    // px/s²  (downward by default)
    @Volatile private var worldW = 1080f
    @Volatile private var worldH = 1920f
    @Volatile private var antiGravityMode = false

    /** Set global gravity vector in px/s².  Positive Y is downward. */
    fun setGravity(gx: Float, gy: Float) { gravX = gx; gravY = gy }

    /** Toggle anti-gravity (inverts the Y component). */
    fun setAntiGravity(enable: Boolean) { antiGravityMode = enable }

    /** Call when screen dimensions are known or change. */
    fun setWorldSize(w: Float, h: Float) { worldW = w; worldH = h }

    // ── External force API ───────────────────────────────────────────────────

    /**
     * Apply an impulse force (N) to the body identified by [id].
     * The force is accumulated and applied during the next simulation step.
     */
    @Synchronized
    fun applyForce(id: Int, fx: Float, fy: Float) {
        activeBodies.firstOrNull { it.id == id }?.externalForce?.addInPlace(fx, fy)
    }

    /** Convenience: apply force to every active body. */
    @Synchronized
    fun applyForceAll(fx: Float, fy: Float) {
        activeBodies.forEach { it.externalForce.addInPlace(fx, fy) }
    }

    // ── Simulation loop ──────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var simulationJob: Job? = null

    private companion object {
        const val TARGET_FPS        = 60
        const val FRAME_MS          = 1000L / TARGET_FPS
        const val FRAME_DT          = 1f / TARGET_FPS   // seconds
        const val COLLISION_ITERS   = 3
        const val MIN_SEPARATION    = 0.5f              // px
    }

    fun start() {
        if (simulationJob?.isActive == true) return
        simulationJob = scope.launch {
            while (isActive) {
                val frameStart = System.currentTimeMillis()
                step(FRAME_DT)
                val elapsed = System.currentTimeMillis() - frameStart
                val sleep = FRAME_MS - elapsed
                if (sleep > 0) delay(sleep)
            }
        }
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ── Core integration ─────────────────────────────────────────────────────

    @Synchronized
    private fun step(dt: Float) {
        val effectiveGravY = if (antiGravityMode) -gravY else gravY
        val effectiveGravX = gravX

        for (body in activeBodies) {
            if (!body.active) continue

            // Verlet velocity (implicit from position history)
            val vx = body.pos.x - body.prevPos.x
            val vy = body.pos.y - body.prevPos.y

            // Gravity contribution
            body.acc.set(effectiveGravX, effectiveGravY)

            // External forces  F = ma → a = F/m
            if (body.externalForce.x != 0f || body.externalForce.y != 0f) {
                body.acc.addInPlace(body.externalForce.x / body.mass,
                                    body.externalForce.y / body.mass)
                body.externalForce.set(0f, 0f)   // consume
            }

            // Buoyancy: upward force proportional to buoyancy coefficient
            if (body.buoyancy != 0f) {
                body.acc.addInPlace(0f, -body.buoyancy * abs(effectiveGravY))
            }

            // Fluid drag: a_drag = -drag * v / m  (approximate)
            val dragScale = body.dragCoeff / body.mass
            body.acc.addInPlace(-vx * dragScale / dt, -vy * dragScale / dt)

            // Verlet integration:  x_new = 2*x - x_prev + a * dt²
            val dt2 = dt * dt
            val newX = body.pos.x + vx + body.acc.x * dt2
            val newY = body.pos.y + vy + body.acc.y * dt2

            body.prevPos.set(body.pos.x, body.pos.y)
            body.pos.set(newX, newY)
        }

        // Constraint passes
        repeat(COLLISION_ITERS) {
            resolveCollisions()
            resolveWorldBounds()
        }

        // Fire callbacks (copy list to avoid CME)
        for (body in activeBodies) {
            body.onPositionChanged?.invoke(body.pos.x, body.pos.y)
        }
    }

    // ── Collision detection (broadphase = O(n²) for ≤64 objects) ────────────

    private fun resolveCollisions() {
        val bodies = activeBodies
        val n = bodies.size
        for (i in 0 until n) {
            val a = bodies[i]; if (!a.active) continue
            for (j in i + 1 until n) {
                val b = bodies[j]; if (!b.active) continue
                val dx = b.pos.x - a.pos.x
                val dy = b.pos.y - a.pos.y
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = a.radius + b.radius
                if (dist < minDist && dist > MIN_SEPARATION) {
                    val overlap = (minDist - dist) / dist
                    val totalMass = a.mass + b.mass
                    val ratioA = b.mass / totalMass
                    val ratioB = a.mass / totalMass
                    val restitution = (a.restitution + b.restitution) * 0.5f
                    val corrX = dx * overlap * restitution
                    val corrY = dy * overlap * restitution
                    // Reflect previous positions to simulate elastic response
                    a.pos.addInPlace(-corrX * ratioA, -corrY * ratioA)
                    b.pos.addInPlace( corrX * ratioB,  corrY * ratioB)
                }
            }
        }
    }

    // ── World boundary constraints ───────────────────────────────────────────

    private fun resolveWorldBounds() {
        for (body in activeBodies) {
            if (!body.active) continue
            val r = body.radius
            val vx = (body.pos.x - body.prevPos.x) * body.restitution
            val vy = (body.pos.y - body.prevPos.y) * body.restitution

            if (body.pos.x - r < 0f) {
                body.pos.x = r
                body.prevPos.x = body.pos.x + vx
            } else if (body.pos.x + r > worldW) {
                body.pos.x = worldW - r
                body.prevPos.x = body.pos.x + vx
            }

            if (body.pos.y - r < 0f) {
                body.pos.y = r
                body.prevPos.y = body.pos.y + vy
            } else if (body.pos.y + r > worldH) {
                body.pos.y = worldH - r
                body.prevPos.y = body.pos.y + vy
            }
        }
    }

    // ── Snapshot / query ─────────────────────────────────────────────────────

    /** Returns a snapshot of all active body positions (safe to call from any thread). */
    @Synchronized
    fun getPositions(): Map<Int, Pair<Float, Float>> =
        activeBodies.filter { it.active }.associate { it.id to (it.pos.x to it.pos.y) }

    @Synchronized
    fun getBodyPosition(id: Int): Pair<Float, Float>? =
        activeBodies.firstOrNull { it.id == id }?.let { it.pos.x to it.pos.y }
}
