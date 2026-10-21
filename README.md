# GIkoko One Recap

[![Build](https://github.com/gikoko611/G-IkokoRecap/actions/workflows/android-build.yml/badge.svg)](https://github.com/gikoko611/G-IkokoRecap/actions/workflows/android-build.yml)

One-click AI Video Recap Android app - မြန်မာအသံ + မြန်မာစာတန်းထိုး။

## Features
- Gemini API key ထည့်ပြီး အသုံးပြု (aistudio.google.com/apikey)
- YouTube link (Gemini နဲ့ တိုက်ရိုက် ခြုံငုံ) ဒါမှမဟုတ် ဖုန်းထဲက video file upload
- Recap အချိန်ရွေး: Auto / 1 / 3 / 5 မိနစ်
- AI အသံမျိုးစုံ (Puck, Charon, Aoede, Zephyr, ...) 12 မျိုး
- မြန်မာစာတန်းထိုး auto generate (SRT file ပါရမယ်)
- အသံ၊ စာ၊ video တိုက်ရိုက်ညီအောင် TTS အချိန်အလိုက် video clip တွေ ဖြတ်တွေ့ပြီး ဖွဲ့စည်းသည်
- Downloads ထဲ သိမ်း / Share

## Build
GitHub Actions က push တိုင်း build လုပ်ပြီး Releases ထဲ APK တင်သည်:
https://github.com/gikoko611/G-IkokoRecap/releases

## Icon / Logo အစစ် ထည့်နည်း
Binary JPEG များကို API နဲ့ မတင်နိုင်လို့ placeholder vector သုံးထားသည်။
- Splash/Home logo: `app/src/main/res/drawable/img_splash_logo.xml` ကိုဖျက်ပြီး `img_splash_logo.png` (သို့) `.jpeg` အမည်တူထည့်ပါ။
- App icon: `ic_monogram.xml` / `ic_launcher_background.xml` ကို လက်တွေ့ပုံအဖြစ်အစားထိုးပါ။

## Notes
- Facebook / TikTok link များကို Gemini က တိုက်ရိုက်မဖတ်နိုင်လို့ video file အဖြစ် upload လုပ်ရမည်။
- Debug-signed APK: install ခံရန် "unknown sources" ခွင့်ပြုပါ။
