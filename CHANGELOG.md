# Changelog

All notable changes to the **VoicePause** project will be documented in this file.

## [1.1.0] - 2024-05-22

### Added
- **Temporal Density Filter**: New noise-filtering logic in `SpeechStateMachine` that requires speech to be "dense" over a 300ms window. This effectively ignores short, non-speech sounds like coughs, sneezes, and loud bangs.
- **Pulsing Status Indicator**: A large, interactive UI element on the Home Screen that pulses when the service is active, providing clear visual feedback.
- **Environment Icons**: Visual icons (🏠, ☕, 🚗) added to profiles to make selection more intuitive.
- **Changelog**: Initial version of this document to track project evolution.

### Changed
- **UI Redesign**: Complete overhaul to a "Google Pixel" / Material You aesthetic. 
    - Replaced RadioButtons with modern, rounded Profile Cards.
    - Updated color palette to soft, dynamic Material 3 colors.
    - Improved typography and spacing for a cleaner, minimal look.
- **Audio Profile Optimization**: 
    - **Quiet Room**: Now more sensitive with faster music resumption.
    - **Busy Room**: Balanced for background chatter with higher VAD aggressiveness.
    - **Traffic/Outdoors**: Maximum noise rejection and higher energy thresholds for loud environments.
- **Refined Speech Detection**: Increased default `minSpeechDuration` to 450ms to ensure voice stability before pausing media.

### Technical
- Updated `SpeechStateMachine` to include a `LinkedList` frame buffer for history-based density calculations.
- Optimized `HomeScreen.kt` with `LazyColumn` and `InfiniteTransition` animations.
- Refined `Theme.kt` and `Color.kt` for better Material 3 support.
