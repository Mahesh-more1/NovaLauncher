# 🚀 NovaLauncher

<div align="center">

![NovaLauncher Banner](https://img.shields.io/badge/NovaLauncher-Android%2012%2B-blue?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
![Build](https://img.shields.io/github/actions/workflow/status/Mahesh-more1/NovaLauncher/build.yml?style=for-the-badge)
![Min SDK](https://img.shields.io/badge/Min%20SDK-31%20(Android%2012)-orange?style=for-the-badge)

**A production-grade, ultra-lightweight custom Android launcher with real-time anti-gravity physics, iOS-aesthetic overlays, and an adaptive widget system.**

*Built for 2GB RAM devices. Zero root required.*

</div>

---

## ✨ Features

### ⚡ Zero-Latency Performance
- Coroutine-driven async app loading — icons appear instantly
- Aggressive memory management with bounded object pools
- Weak references throughout — zero memory leaks
- Optimized for Unisoc + 2GB RAM architecture

### 🌊 Custom Physics Engine
- **Verlet integration** — smooth, realistic icon motion
- **Anti-gravity mode** — icons float upward with buoyancy
- **Fluid dynamics** — drag resistance per icon
- **Collision detection** — icons bounce off each other
- Runs entirely off the main thread via Kotlin coroutines

### 🍎 iOS-Style System UI Overlay *(No Root!)*
- **Quick Settings panel** — frosted glass, 8 iOS-style tiles
- **Notification shade** — live notifications, swipe to dismiss
- **Brightness & Volume sliders** — iOS-style seekbars
- Smooth pull-down animation (380ms show / 280ms hide)
- Uses only public Android APIs — no root, no ROM mod needed

### 🧩 Adaptive Widget System
- Supports all Android widgets via `AppWidgetHost`
- SurfaceView-backed rendering — zero memory overhead off-screen
- Widget categories: Clock, Weather, Music, Calendar, Battery, Steps
- Touch-based resizing with corner handles
- Persists widget layout across reboots

### 🎨 Beautiful UI
- iOS 17 HIG color palette
- Frosted glass dock and search bar
- Smooth page transitions (ViewPager2)
- Physics-animated icon drag & drop
- Full-screen immersive mode

---

## 📁 Project Structure

```
NovaLauncher/
├── app/
│   ├── src/main/
│   │   ├── java/com/novalauncher/launcher/
│   │   │   ├── LauncherActivity.kt       # Main home screen
│   │   │   ├── PhysicsEngine.kt          # Anti-gravity physics
│   │   │   ├── AppAdapter.kt             # App grid RecyclerView
│   │   │   ├── AppInfo.kt                # App data model
│   │   │   ├── GestureHandler.kt         # Swipe/tap gestures
│   │   │   ├── SystemUIOverlay.kt        # iOS Quick Settings
│   │   │   ├── WidgetManager.kt          # Widget hosting
│   │   │   ├── ui/
│   │   │   │   ├── AppDrawerFragment.kt  # Full app drawer
│   │   │   │   ├── DockView.kt           # Bottom dock bar
│   │   │   │   └── PageIndicatorView.kt  # Home page dots
│   │   │   ├── service/
│   │   │   │   ├── NovaNotificationListenerService.kt
│   │   │   │   └── BootReceiver.kt       # Auto-start on boot
│   │   │   └── utils/
│   │   │       ├── BlurUtils.kt          # Frosted glass rendering
│   │   │       ├── AppPreferences.kt     # Settings persistence
│   │   │       └── IconPackManager.kt    # Icon pack support
│   │   ├── res/
│   │   │   ├── layout/                   # All XML layouts
│   │   │   ├── values/                   # Colors, strings, styles
│   │   │   ├── drawable/                 # Vector drawables
│   │   │   ├── anim/                     # Animations
│   │   │   └── xml/                      # App configs
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/build.yml           # Auto-build APK
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 Building the APK (No Local Setup Required!)

This project uses **GitHub Actions** to build automatically in the cloud.

### Method 1: GitHub Actions (Recommended — Zero Setup)
1. Push any change to the `main` branch
2. Go to **Actions** tab on GitHub
3. Wait 3–5 minutes for the build
4. Download `novalauncher-release.apk` from **Artifacts**

### Method 2: GitHub Codespaces (Edit + Build in Browser)
1. Click **Code** → **Codespaces** → **New codespace**
2. In the terminal: `./gradlew assembleDebug`
3. Download the APK from `app/build/outputs/apk/debug/`

---

## 📱 Installation

1. Download the APK from GitHub Actions Artifacts
2. On your Android phone: **Settings → Apps → Special app access → Install unknown apps**
3. Enable for your browser/file manager
4. Open the APK → **Install**
5. **Settings → Apps → Default apps → Home app → NovaLauncher**

### First Launch Permissions
| Permission | Why Needed |
|---|---|
| Notification access | iOS-style notification shade |
| Display over other apps | Quick Settings overlay |
| Auto-start (optional) | Launch on boot |

---

## 🛠️ Tech Stack

| Technology | Usage |
|---|---|
| **Kotlin** | 100% Kotlin codebase |
| **Coroutines** | Async physics + app loading |
| **ViewPager2** | Home screen pages |
| **RecyclerView** | App grid with DiffUtil |
| **AppWidgetHost** | Widget rendering |
| **WindowManager** | System UI overlay |
| **NotificationListenerService** | Live notifications |
| **Coil** | Lightweight image loading |
| **Verlet Integration** | Physics simulation |
| **SurfaceView** | Zero-overhead widget rendering |

---

## 📋 Requirements

- Android 12+ (API 31+)
- 2GB RAM minimum (optimized for it)
- No root required
- No custom ROM required

---

## 🗺️ Roadmap

- [ ] Icon pack support (ADW, Nova compatible)
- [ ] Dark / Light mode toggle in Quick Settings
- [ ] Weather widget integration
- [ ] Gesture-based app shortcuts
- [ ] Home screen backup & restore
- [ ] Custom dock icon slots
- [ ] Theme engine

---

## 📄 License

```
MIT License — Copyright (c) 2024 Mahesh More
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software to use, copy, modify, merge, publish, distribute — without restriction.
```

---

<div align="center">
Built with ❤️ using Antigravity AI
</div>

