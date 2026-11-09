# Car Launcher

Dark 3D launcher for a car Android head unit: speedometer, digital clock, weather,
location, distance travelled, battery voltage and an editable dock.

## Get the APK (no Android Studio)
1. Create a new GitHub repository and upload everything in this folder
   (keep the `.github/workflows/build.yml` path).
2. Open the repo, tab **Actions**, wait for **Build APK** to finish (or press **Run workflow**).
3. Open the finished run and download the artifact **CarLauncher-apk**. Unzip it to get `app-debug.apk`.
4. Copy the APK to the head unit (USB stick), install it, open it once and allow Location.
5. Press the unit's Home button and choose **Car Launcher** as the home app (Always).
   If you want your old home screen back: Android Settings > Apps > Default apps > Home app.

## Notes
- Speed and distance come from GPS. Distance counts only while you are moving.
- Weather and the place name need internet (Wi-Fi or phone hotspot).
- Battery voltage uses the standard Android reading. Many head units do not expose the car
  battery voltage this way and will show "-- V".
- Long-press a dock tab, or tap Edit, to remove tabs. "+ Add tab" lets you pick any installed app.
  "Reset all" restores the default dock.
