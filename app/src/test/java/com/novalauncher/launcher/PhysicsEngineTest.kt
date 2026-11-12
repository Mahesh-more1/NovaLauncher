package com.novalauncher.launcher

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * Comprehensive JUnit4 unit tests for [PhysicsEngine].
 *
 * Verifies:
 * - Body addition and position querying
 * - Body removal and resource reclamation
 * - Bounded object pool limits and allocation reuse
 * - Gravity and anti-gravity vector switching
 * - External force application (impulse and accumulation)
 * - Boundary containment and collision with world bounds
 * - Lifecycle management (start/stop/destroy)
 */
class PhysicsEngineTest {

    private lateinit var engine: PhysicsEngine
    private lateinit var stepMethod: Method

    @Before
    fun setUp() {
        engine = PhysicsEngine(maxBodies = 10)
        // Access private step(dt: Float) method for fast, deterministic unit testing
        stepMethod = PhysicsEngine::class.java.getDeclaredMethod("step", Float::class.java).apply {
            isAccessible = true
        }
    }

    @After
    fun tearDown() {
        engine.destroy()
    }

    private fun step(dt: Float = 1f / 60f) {
        stepMethod.invoke(engine, dt)
    }

    @Test
    fun testAddBody() {
        val bodyId = engine.addBody(x = 100f, y = 200f, mass = 1f, radius = 36f)
        assertTrue("Body ID should be non-negative", bodyId >= 0)

        val pos = engine.getBodyPosition(bodyId)
        assertNotNull("Position should not be null for active body", pos)
        assertEquals("Initial X position mismatch", 100f, pos!!.first, 0.001f)
        assertEquals("Initial Y position mismatch", 200f, pos.second, 0.001f)

        val allPositions = engine.getPositions()
        assertTrue("Snapshot map must contain body id", allPositions.containsKey(bodyId))
        assertEquals(1, allPositions.size)
    }

    @Test
    fun testRemoveBody() {
        val bodyId = engine.addBody(x = 150f, y = 250f)
        assertTrue(bodyId >= 0)
        assertNotNull(engine.getBodyPosition(bodyId))

        engine.removeBody(bodyId)
        assertNull("Removed body should return null position", engine.getBodyPosition(bodyId))
        assertFalse("Snapshot should not contain removed body", engine.getPositions().containsKey(bodyId))
    }

    @Test
    fun testObjectPoolLimit() {
        val smallEngine = PhysicsEngine(maxBodies = 3)
        try {
            val id0 = smallEngine.addBody(10f, 10f)
            val id1 = smallEngine.addBody(20f, 20f)
            val id2 = smallEngine.addBody(30f, 30f)

            assertTrue(id0 >= 0)
            assertTrue(id1 >= 0)
            assertTrue(id2 >= 0)

            // Exceeding pool limit must return -1 without throwing
            val idOverflow = smallEngine.addBody(40f, 40f)
            assertEquals("Pool overflow should return -1", -1, idOverflow)
            assertEquals(3, smallEngine.getPositions().size)

            // Releasing one body must allow allocating again from pool
            smallEngine.removeBody(id1)
            val idReused = smallEngine.addBody(50f, 50f)
            assertTrue("Should be able to re-allocate from pool after removal", idReused >= 0)
            assertEquals(3, smallEngine.getPositions().size)
        } finally {
            smallEngine.destroy()
        }
    }

    @Test
    fun testGravityToggle() {
        engine.setWorldSize(2000f, 2000f)
        engine.setGravity(0f, 980f) // downward gravity

        val bodyId = engine.addBody(x = 500f, y = 500f, mass = 1f)

        // Step simulation forward
        repeat(10) { step() }

        val posAfter = engine.getBodyPosition(bodyId)!!
        assertTrue("Y position should increase under downward gravity", posAfter.second > 500f)
        assertEquals("X position should remain unchanged under purely vertical gravity", 500f, posAfter.first, 0.01f)
    }

    @Test
    fun testAntiGravityToggle() {
        engine.setWorldSize(2000f, 2000f)
        engine.setGravity(0f, 980f)

        val bodyId = engine.addBody(x = 500f, y = 500f, mass = 1f)

        // Enable anti-gravity: should invert Y acceleration upwards
        engine.setAntiGravity(true)

        repeat(10) { step() }

        val posAfter = engine.getBodyPosition(bodyId)!!
        assertTrue("Y position should decrease under anti-gravity", posAfter.second < 500f)
        assertEquals("X position should remain unchanged", 500f, posAfter.first, 0.01f)
    }

    @Test
    fun testApplyForce() {
        engine.setWorldSize(2000f, 2000f)
        engine.setGravity(0f, 0f) // disable gravity to isolate external force

        val bodyId = engine.addBody(x = 300f, y = 300f, mass = 1f)

        // Apply impulse to the right (+X)
        engine.applyForce(bodyId, fx = 10000f, fy = 0f)

        step()

        val posAfter = engine.getBodyPosition(bodyId)!!
        assertTrue("Body should accelerate in positive X direction", posAfter.first > 300f)
        assertEquals("Body Y should stay unchanged", 300f, posAfter.second, 0.001f)
    }

    @Test
    fun testBoundaryConstraints() {
        val worldW = 500f
        val worldH = 800f
        val radius = 36f
        engine.setWorldSize(worldW, worldH)
        engine.setGravity(0f, 2000f)

        // Place body near the bottom boundary
        val bodyId = engine.addBody(x = 250f, y = worldH - radius - 5f, radius = radius, restitution = 0.5f)

        // Step simulation extensively to force collision with floor
        repeat(60) { step() }

        val pos = engine.getBodyPosition(bodyId)!!
        val maxAllowedY = worldH - radius

        assertTrue(
            "Body center Y (${pos.second}) should not exceed world floor minus radius ($maxAllowedY)",
            pos.second <= maxAllowedY + 0.1f
        )
        assertTrue(
            "Body center Y (${pos.second}) should stay within world bounds",
            pos.second >= radius
        )
    }

    @Test
    fun testLifecycleStartAndStop() {
        engine.start()
        // Calling start again when active should be a safe no-op
        engine.start()
        engine.stop()
        // Calling stop when already stopped should be a safe no-op
        engine.stop()
    }
}

