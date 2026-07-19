# PartOut

Point your camera at a car part and let AI do the rest.

**What it does:**

- **Scan** — photograph any automotive part; Gemini identifies it, grades its condition, estimates its used-market value, and drafts a marketplace listing.
- **Mechanic** — an AI repair chat for *your* vehicle. Describe a symptom (optionally attach a photo), and get ranked likely causes, how to test them, and step-by-step repair instructions with tools and parts.
- **Pull Guide** — step-by-step removal instructions for a scanned part, with fastener locations marked on your photo.
- **Garage Logs** — every scan is saved on-device, so your history survives app restarts.
- **Part-Outs** — track a salvage vehicle's pull list, listing status, and total estimated payout.

## Run it

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio, select **Open**, and choose this project directory. Let it sync (it will generate the Gradle wrapper if prompted).
2. Run the app on a device or emulator (a physical phone is best — you'll want the camera).

## Set up the AI (required for scanning and the Mechanic chat)

You need a free Google Gemini API key:

1. Go to [aistudio.google.com/apikey](https://aistudio.google.com/apikey) and create a key (takes about a minute).
2. **Easiest:** just launch the app — the first time you use an AI feature it will ask for the key. Paste it in. It's stored only on the device and you're done.
3. **Alternative (bake it into the build):** create a file named `.env` in the project root containing `GEMINI_API_KEY=your-key-here` (see `.env.example`).

## Tips

- In the **Mechanic** tab, fill in the "Your vehicle" field (e.g. `1997 Jeep Wrangler TJ 4.0L`) — every answer gets anchored to that exact vehicle.
- On a scan result, use **Ask AI Mechanic** to jump straight into repair help for that part with the photo attached.
- AI guidance can be wrong. Verify torque specs and anything safety-critical (brakes, fuel, airbags, jack points) against a factory service manual.
