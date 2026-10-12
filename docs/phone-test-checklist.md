# Testing Screenshot Sorter on the phone

The app has only been tested on a computer so far. This is the first run on a real phone, so please go through every step.

## Install

1. Send `ScreenshotSorter-1.0.apk` to the phone (Telegram works) and open it.
2. If the phone asks, allow installing apps from this source.
3. On Xiaomi, wait for the security scan. If it warns about an unknown app, choose to install anyway.

## Set up

4. Open Screenshot Sorter.
5. Tap **Allow** next to *Files access* and switch it on. Go back to the app.
6. Tap **Allow** next to *Run in the background* and confirm.
7. Xiaomi, Redmi, POCO: tap **Open** next to *Autostart* and switch on Screenshot Sorter. Then open the app's App info and set *Battery saver* to *No restrictions*.
8. If there is a *Notification* row with an **Allow** button, tap it and allow. This one is optional.
9. Turn on the big switch at the top. From now on the app sorts the screenshots you take. The ones already in the gallery stay where they are until you tap **Sort old screenshots** (checks D and G).

## Check

- **A.** Take a screenshot in Telegram. Wait 15 seconds. Open the gallery's Albums. Is there a "Telegram" album with the screenshot in it?
- **B.** Take a screenshot in another app. Same check.
- **C.** Take a screenshot and immediately tap the small preview to edit or share it. Does that still work?
- **D.** Old screenshots. The app only sorts screenshots taken after you turned the big switch on. Is a screenshot that was already in the gallery before that still in the main Screenshots album, not in an app album? Then tap **Sort old screenshots**. Check G below goes through this button step by step, so do D and G together. Does the number look right? After confirming, are the old screenshots in app albums?
- **E.** Restart the phone and do not open Screenshot Sorter. Take a screenshot. Is it sorted within a minute? If not, is it sorted after 20 minutes?
- **F.** The next day: is it still sorting?

Checks G to K can be done on the first day. You do not have to wait for F. They look at the boxes, the colours and the switch, which the computer tests could not show.

- **G.** The boxes of **Sort old screenshots**. Do this together with D, on the same old screenshots, and do not take a screenshot while you do it. Go through the boxes in this order:
  1. Tap **Sort old screenshots**. A small spinning circle appears next to the button while it counts, and the button goes grey. On a small gallery this can be too quick to see. That is fine.
  2. A box asks "Sort … screenshots?" and says nothing is deleted. Tap **Cancel**. The box closes and nothing moves.
  3. Tap **Sort old screenshots** again, then tap **Sort**. The spinning circle shows again while it works.
  4. A box says "Moved … screenshots". Is it the same number as in the question? Look at D now: are the old screenshots in app albums? Tap **OK**.
  5. Tap **Sort old screenshots** once more. A box should say "Nothing to sort". Tap **OK**.

  If the phone had no old screenshots, step 2 shows "Nothing to sort" straight away. Then do J first, which leaves one screenshot waiting on purpose, and come back to G and D.
- **H.** Nothing hidden at the edges. Open the app. Is the title "Screenshot Sorter" fully visible, below the clock and battery icons? Scroll down to the very end. Is the last line of the *Recent* list fully visible, above the buttons or the gesture line at the bottom of the screen? Is anything cut off or covered?
- **I.** Dark mode. Switch the phone to dark mode (Settings, Display) and open the app. Is the background dark and everything easy to read: the text, the buttons, the ticks, the big switch? Open the **Sort old screenshots** box too. Does the app stay green, whatever the wallpaper? Then switch back to light mode and look once more.
- **J.** The big switch off. Turn it off. It should say "Sorting is off". The small "Screenshot Sorter is on" note at the top of the phone should go away, if you allowed notifications. Take a screenshot in Telegram and wait 30 seconds. Open the gallery: the screenshot must still be in the main Screenshots album, not in an app album. Now turn the switch back on and wait a minute. The screenshot is still in the main album, and that is right: the app only sorts screenshots taken after you turn it on. Tap **Sort old screenshots**. The box should count it. Tap **Sort**. Is it now in the "Telegram" album? Last, take one more screenshot. Is it sorted after 15 seconds, as in A?
- **K.** Only if the *Usage access* row shows an **Allow** button (on a Redmi it says "Not needed on this phone", so skip this one). Tap **Allow**, switch on Screenshot Sorter in the list, and go back to the app. Has the **Allow** button turned into a tick? On Android 15 or newer, if the switch is greyed out, follow the note written under *Usage access* in the app, then try again.

## Tell me

- Phone model and Android / MIUI / HyperOS version.
- Which of A to K did not work, and what you saw instead.
- A screenshot of the app's *Recent* list.
- A screenshot of anything that looked cut off or hard to read.
