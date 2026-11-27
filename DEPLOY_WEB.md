# Deploy the Talker web app

## Important: this is not a static-only website

The React interface in `web/` calls the Gradio API implemented by
`voicetut_tts/server.py`. The production UI is built into
`voicetut_tts/webui/` and served by the Python application at the same origin.
Uploading only the generated HTML/JavaScript to GitHub Pages, Netlify Static, or
another static host will display an interface that cannot synthesize audio.

Use a **Gradio Hugging Face Space** to host the complete application and its
Python inference backend.

## 1. Create the Space

1. Open https://huggingface.co/new-space.
2. Create a Space under your Hugging Face account.
3. Select **Gradio** as the SDK and make the Space public or private as needed.
4. The repository root in this GitHub branch contains the Space metadata in
   `README.md` and runtime packages in `requirements.txt`.
5. The model checkpoint is downloaded from
   `mohammedaly22/VoiceTut-TTS` when the app starts. The Space must be allowed
   to access that model if its visibility or access rules change.

The model needs GPU acceleration for practical use. Choose **ZeroGPU** in the
Space Settings when it is available to your account; this project already uses
the `@spaces.GPU` decorator required by ZeroGPU. Eligible free personal
accounts in good standing can host up to two ZeroGPU Spaces. A dedicated GPU
runtime is an alternative, but it is billed to the Space owner by the hour.
CPU-only runtime is not suitable for practical synthesis and may fail to load
the model within available memory. Hardware selection is controlled in Space
Settings and is not automatically assigned by this repository.

## 2. Allow GitHub Actions to deploy

Create a Hugging Face access token with **write** permission for the Space.
In this GitHub repository, open **Settings → Secrets and variables → Actions**
and add:

| Secret | Value |
|---|---|
| `HF_TOKEN` | The Hugging Face write token |
| `HF_SPACE_ID` | Space repository ID in `account/space-name` format |

Do not put the token in source files, commits, or workflow YAML.

## 3. Deploy

1. Push or merge this branch into the branch you intend to deploy.
2. Open **GitHub → Actions → Deploy web app to Hugging Face Space**.
3. Choose **Run workflow** and select that branch.
4. When the workflow finishes successfully, open your Space URL:
   `https://huggingface.co/spaces/<account>/<space-name>`.

The workflow installs the web dependencies, builds the React interface into
`voicetut_tts/webui/`, performs a Python syntax check, then uploads the
application to the Space. A new Space commit triggers its build and restart.
The Space uses ZeroGPU request allocation through `@spaces.GPU`; the creator
and visitors are subject to Hugging Face's current ZeroGPU eligibility and
usage quotas.

## 4. Local preview

On Windows 11, from the repository folder:

```powershell
py -3.10 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[web]"
$env:VOICETUT_MOCK = "1"
python app.py
```

Open http://localhost:7860. Mock mode is only for previewing the interface:
it generates synthetic test audio and is **not** real speech synthesis.

For a local production build of the React UI:

```powershell
cd web
npm ci
npm run build
cd ..
python app.py
```

Unset `VOICETUT_MOCK` and install the model backbone plus the appropriate
PyTorch build before attempting real local inference on a compatible GPU.
