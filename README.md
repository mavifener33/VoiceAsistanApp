# VoiceAssistantApp

Basit bir Android (Kotlin) sesli asistan örneği.

Özellikler:
- Cihaz mikrofonundan Speech-to-Text ile metin alır.
- OpenAI Chat API'ye gönderir ve yanıtı TextToSpeech ile seslendirir.

## Nasıl çalıştırılır

1. Android Studio'da projeyi aç: `voice-assistant-app`
2. `gradle.properties` içine OpenAI anahtarını ekleyin:
   ```
   OPENAI_API_KEY="sk-..."
   ```
   veya `local.properties` yerine güvenli bir yöntem kullanın. Bu örnek, BuildConfig üzerinden anahtarı okumayı bekler; üretimde anahtarı uygulama içine gömmeyin.
3. Cihazda mikrofon izni verin.
4. Uygulamayı çalıştırın. "Dinlemeyi Başlat" butonuna basın.

## Güvenlik uyarısı
- Uygulamaya doğrudan API anahtarı koymayın. Üretimde bir backend proxy kullanın.

