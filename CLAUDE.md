# Contexto do projeto (fork rafaelsant/tuxguitar)

## Objetivo
Usar o TuxGuitar (editor de tablatura open source em Java, LGPL) como app Android de prática de guitarra: loop de trecho e aumento gradual de velocidade (modo treino), como no Guitar Pro. O desenvolvimento é feito principalmente pelo celular. Fork: `rafaelsant/tuxguitar` (upstream: `helge17/tuxguitar`, versão 2.1.0).

## Estrutura do app Android
- Projeto Gradle: `android/build-scripts/tuxguitar-android` (AGP 8.7.3, wrapper Gradle 8.9). Módulo do app: `:apk`.
- minSdk 24, targetSdk/compileSdk 35, sem product flavors (só debug e release).
- Saída do debug: `android/build-scripts/tuxguitar-android/apk/build/outputs/apk/debug/*.apk`.
- `.github/workflows/android-gradle.yml` é do upstream (JDK 21, `assembleRelease` em todo push). **Não alterar**, para evitar conflito ao sincronizar com o upstream.
- `.github/workflows/android-debug-apk.yml` (deste fork) gera o APK debug: roda em push para branches `android-*` e publica o artefato `tuxguitar-debug-apk` na página da execução no Actions. O APK debug usa chave de debug; se houver outro APK instalado com assinatura diferente, é preciso desinstalá-lo antes.

## Textos e idiomas
- `android/TuxGuitar-android/res/values/strings.xml` (inglês) é editado à mão.
- `res/values-b+*/strings.xml` são gerados por `misc/messages_desktop_to_android.pl` a partir de `common/resources/lang/messages_*.properties`. Não editar à mão; strings novas sem tradução caem no inglês.
- O idioma do app é escolhido em Configurações do Android → Apps → TuxGuitar → Idioma (Android 13+), via `res/xml/locales_config.xml`.

## O que o app já tem
- Diálogo Transport Mode (`android/TuxGuitar-android/src/app/tuxguitar/android/view/dialog/transport/TGTransportModeDialog.java`):
  modo Simple (porcentagem de tempo de 1 a 500 e "Play looped") e modo Trainer (tempo inicial, final e incremento, em %). Loop Range por compasso (De/Até; compassos com marcador mostram o título).
- Menu do player (`TGTransportMenu`): play, stop, metrônomo, count-in, abrir Transport Mode, "Set loop start/end point" a partir do compasso do cursor.
- A verificar: quando o incremento do Trainer é aplicado (suspeita: a cada volta do loop); loop é por compasso, não por seleção livre; issue aberta #1146 (repetições em modo loop).
- A UI é Views/XML com Spinners de 1 a 500, pouco amigável no celular.

## Próximos passos
- UX da tela de prática: antes de mexer no código, propor o redesign (Material 3, tema escuro, uso com uma mão enquanto toca, alvos de toque grandes, controles de loop e velocidade na tela do player, sliders/botões no lugar dos spinners). Mostrar o plano e esperar aprovação.

## Regras
- Mudanças em branches, commits pequenos, nada no master do upstream.
- Se algo não puder ser verificado (ex.: build), dizer explicitamente em vez de supor.
