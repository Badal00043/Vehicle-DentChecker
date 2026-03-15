# Vehicle-DentChecker-AI 🚗💨

**Intelligent, AI-Powered Automated Vehicle Damage Inspection & Assessment**

Vehicle-DentChecker-AI is a state-of-the-art mobile solution designed to eliminate the subjectivity and inefficiency of manual vehicle inspections. Built for insurance claims, dealer intake, and fleet management, it leverages a sophisticated two-stage AI pipeline to detect, localize, and assess physical damages in real-time.

---

## 🌟 Key Features

### 1. **High-Fidelity Dashboard**
- **Fleet Health Gauge:** Real-time visualization of overall fleet condition (e.g., "92% Healthy").
- **Recent Inspections:** Live feed of the latest vehicle scans with damage summaries.

### 2. **Guided Walkaround Wizard**
- **Category Selection:** Specialized profiles for **4-Wheelers**, **Motorcycles (2W)**, and **Rickshaws (3W)**.
- **View Angle Control:** Guided steps to capture specific views (Front, Rear, Side, Engine, Interior) to ensure optimal data quality.

### 3. **Dual-Input Mode**
- Capture live high-resolution photos via the **Integrated Camera**.
- Upload existing evidence from the **Smartphone Gallery**.

### 4. **Advanced AI Pipeline (The Invisible Engine)**
- **Stage 1: YOLOv11 Localization:** Scans the image structure to find exact coordinates of dents, cracks, and scratches.
- **Stage 2: Gemini 1.5 Flash Intelligence:** Performs high-level reasoning to differentiate between **Cosmetic** and **Functional** damage.
- **Explainability:** Visual overlays including **Neon Bounding Boxes** and toggleable **AI Heatmaps**.

### 5. **Actionable Insights**
- **Severity Levels:** Low, Medium, and High impact classifications.
- **Repair Estimations:** Automatic mock-up of repair costs based on damage type.
- **Export Capabilities:** Option to generate comprehensive digital reports.

---

## 🛠️ Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Modern Declarative UI)
- **Networking:** [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Architecture:** MVVM (Model-View-ViewModel) with StateFlow
- **AI Backend:**
    - **Localization:** YOLOv11 (Ultralytics via Hugging Face API)
    - **Intelligence:** Gemini 1.5 Flash (via Google Generative AI SDK)
- **Theme:** High-fidelity Cyberpunk/Neon Aesthetic (#0B1015 / #00FFFF)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or later.
- JDK 17+.
- A valid **Gemini API Key** (from [Google AI Studio](https://aistudio.google.com/)).
- A **Hugging Face Access Token** (for YOLOv11 inference).

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Badal00043/Vehicle-DentChecker.git
   ```

2. **Secure your API Tokens:**
   Create a `local.properties` file in your project root and add your tokens:
   ```properties
   HF_TOKEN=hf_your_token_here
   ```
   *Note: The `GEMINI_API_KEY` is currently configured in `MainActivity.kt` for hackathon purposes.*

3. **Sync and Run:**
   - Open the project in Android Studio.
   - Click **"Sync Project with Gradle Files"**.
   - Press **Run** (Play button) to deploy to your device.

---

## 📸 User Journey

1. **Secure Login:** Access the encrypted dashboard via the futuristic Sign-In screen.
2. **Select Context:** Choose your vehicle type and the angle you are inspecting.
3. **Scan:** Take a photo or upload one.
4. **Analyze:** Watch the AI localize damages in seconds.
5. **Report:** Review the visual overlays and generate a structured business report.

---

## 👤 Author

**Badal Kumar**  
*B.Tech Student | AI & Mobile Developer*  
[GitHub Profile](https://github.com/Badal00043)

---

## 📄 License

This project is developed for hackathon purposes. Please contact the author for commercial usage or redistribution inquiries.
