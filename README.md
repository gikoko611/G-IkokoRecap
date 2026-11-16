# G.I AI

**G.I AI** - on-device AI chat app by **I KO KO**. ChatGPT-style UI with a **neon cyber dark green** theme.

## Features
- 💬 Chat with any **custom AI provider** (OpenAI-compatible base URL + API key; OpenRouter recommended)
- 🆓 **FREE MODELS ONLY** filter (fetch + pick `:free` models)
- ➕ **+ button** next to the input: upload images (vision), videos, files
- 🎬 **3D ANIMATION VIDEO ENGINE**: AI writes a Three.js scene, the app renders frames and encodes on-device H.264, then saves **mp4 + frames ZIP + scene HTML** to Downloads/GIAI (runs in background, only a small status chip on the chat page)
- 🖥 **Termux bridge**: AI replies with a ```termux code block, the app executes it inside Termux (use any Termux tool: ffmpeg, python, node...)
- 🐙 **GitHub access**: token in Settings, read / write / create / delete files, list repos, dispatch + read GitHub Actions
- 📱 Phone access permissions + network access from Settings
- ⬇ Auto-download of every generated output (mp4, zip, images, code files)
- 🌟 3D animated splash: **G.I** logo + AI rings, **Powered by I KO KO**

## Setup
1. Settings, **AI PROVIDER**: base URL + API key (e.g. `https://openrouter.ai/api/v1`), tap FETCH MODELS, pick a model (FREE ONLY switch available), TEST.
2. Settings, **GITHUB**: paste a Personal Access Token (repo scope), set default repo, test READ/WRITE/DELETE/RUN WORKFLOW.
3. Settings, **TERMUX**: install Termux, run `pkg install termux-api ffmpeg python nodejs`, set `allow-external-apps=true` in `~/.termux/termux.properties`, then TEST COMMAND.
4. Settings, **PHONE ACCESS**: GRANT permissions.

## 3D contract for AI scenes
```html
<script src="https://unpkg.com/three@0.160.0/build/three.min.js"></script>
<script>
  window.giTotal = 90;              // total frames
  window.giFrame = function (t) {   // t = 0..1, deterministic
    // update your Three.js scene for time t
  };
</script>
```

Powered by **I KO KO** 🟢