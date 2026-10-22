# GIkoko One Recap (Pro v2.0)

AI video recap app - YouTube link ဒါမှမဟုတ် ဖုန်းထဲက video file ကနေ **အရေးကြီးတဲ့ အပိုင်းတွေချုံ**၊ မြန်မာ AI အသံ + မြန်မာစာတန်းထိုးနဲ့ တစ်ပွင့်တည်း recap video လုပ်ပေးတဲ့ Android app။

## Features (Pro v2.0)

- YouTube link နဲ့ recap (Gemini က video ကိုတိုက်ရိုက်လုပ်ဆောင်)
- ဖုန်းထဲက video file upload (MP4/MOV/MKV/WebM/AVI/3GP) - Facebook/TikTok video တွေအတွက်
- Myanmar AI voice မျိုးစုံ (Puck, Charon, Fenrir, Kore ... 12 မျိုး)
- အသံ PREVIEW - မရွေးခင် စမ်းနားထောင်နိုင်
- မြန်မာစာတန်းထိုး (SRT) အလိုအလျောက်ထွက်
- Recap အချိန် ရွေးနိုင် - Auto / 1 / 3 / 5 မိနစ်
- Segments mode - Compact (4-6) သို့ Full (6-12)
- Narration ဘာသာ - မြန်မာ / English
- Video quality - 720p / 1080p
- Watermark ထည့်/ပယ်ဖျက်နိုင်
- Intro / Outro branded card များ
- Auto-save to Downloads (သော့ဖွင့်နိုင်)
- History - လုပ်ခဲ့တဲ့ recap အားလုံး ပြန်ကြည့် / ပြန်ဖွင့် / မျှဝေ / ဖျက်
- Settings page - API key ထည့်/စစ်ဆေး၊ model ရွေး (gemini-2.5-flash / gemini-2.0-flash)
- Left drawer menu (ဘယ်ဘက်နှိပ် ဆွဲ)
- Haptic feedback
- Cache ရှင်းလင်းမှု
- Share - Video / SRT / Text summary
- Retry on error
- Signed release APK (install မလုပ်နိုင်တာ ပြေလည်)

## Gemini API Key

1. https://aistudio.google.com/apikey မှာ အကောင့်ဝင်ပြီး API key တစ်ခု ဖန်တီးပါ
2. App ထဲမှာ ဘယ်ဘက် ≡ ကိုနှိပ် → Settings → API key ထည့် → SAVE → TEST KEY
3. 403 error ပေါ်ရင် - key မှားနေတာ ဒါမှမဟုတ် region block ဖြစ်နေတာပါ (VPN သုံးပြီး စမ်းကြည့်ပါ)

## Download APK

CI အလိုအလျောက် build လုပ်ပြီး signed APK release တင်ပေးမယ် -
https://github.com/gikoko611/G-IkokoRecap/releases

GIkokoOneRecap-release.apk ကို ရွေးချယ်ပါ။

## Icon / Logo အစားထိုးခြင်း (စိတကြိုက်ပုံနဲ့)

App icon နဲ့ logo ဟာ vector placeholder တွေပါ။ ကိုယ်ပိုင် JPEG ပုံတွေနဲ့ အစားထိုးချင်ရင် -

1. GitHub web မှာ ဒီ repo ကိုဖွင့်ပါ
2. app/src/main/res/drawable-nodpi/ folder အသစ်တည်ဆောက်ပါ (Add file → Create new file မှာ path ရိုက်)
3. ပုံဖိုင်တွေကို upload ပြီး နာမည်တွေကို ဒီအတိုင်းပေးပါ -
   - app_icon.jpg (512x512, square)
   - app_logo.jpg (1280x720, 16:9)
4. ပုံတွေကို အသုံးချချင်ရင် layout တွေထဲမှာ ImageView src အနေနဲ့ ညွှန်းပါ

Vector drawables (ic_monogram.xml, img_splash_logo.xml) က gold/black cyber style နဲ့ ကိုယ်တိုင်ဆွဲထားတာပါ။

## Tech

- Kotlin, AndroidX, Material Components
- Google Gemini API (generateContent + TTS)
- Media3 Transformer (video composition + overlays)
- GitHub Actions CI (signed APK auto-release)

## Developer

I Ko Ko - GIkoko One Recap
