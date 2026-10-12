# Contexto do projeto (fork rafaelsant/tuxguitar)

## Objetivo
Usar o TuxGuitar (editor de tablatura open source em Java, LGPL) como app Android de prática de guitarra: loop de trecho e aumento gradual de velocidade (modo treino), como no Guitar Pro. O desenvolvimento é feito principalmente pelo celular. Fork: `rafaelsant/tuxguitar` (upstream: `helge17/tuxguitar`, versão 2.1.0).

## Estrutura do app Android
- Projeto Gradle: `android/build-scripts/tuxguitar-android` (AGP 8.7.3, wrapper Gradle 8.9). Módulo do app: `:apk`.
- minSdk 24, targetSdk/compileSdk 35, sem product flavors (só debug e release).
- Saída do debug: `android/build-scripts/tuxguitar-android/apk/build/outputs/apk/debug/*.apk`.
- `.github/workflows/android-gradle.yml` é do upstream (JDK 21, `assembleRelease` em todo push). **Não alterar**, para evitar conflito ao sincronizar com o upstream.
- `.github/workflows/android-debug-apk.yml` (deste fork) gera o APK debug: roda em push para branches `android-*` e publica o APK na release `android-latest` (substituída a cada build): https://github.com/rafaelsant/tuxguitar/releases/download/android-latest/tuxguitar-debug.apk. O APK debug usa chave de debug; se houver outro APK instalado com assinatura diferente, é preciso desinstalá-lo antes.

## Textos e idiomas
- `android/TuxGuitar-android/res/values/strings.xml` (inglês) é editado à mão.
- `res/values-b+*/strings.xml` são gerados por `misc/messages_desktop_to_android.pl` a partir de `common/resources/lang/messages_*.properties`. Não editar à mão; strings novas sem tradução caem no inglês.
- O idioma do app é escolhido em Configurações do Android → Apps → TuxGuitar → Idioma (Android 13+), via `res/xml/locales_config.xml`.

## O que o app já tem
Branch de trabalho: `android-apk` (cada push gera o APK debug). UI em Jetpack Compose (Material 3, Kotlin 2.0, BOM 2024.12), tema claro/escuro seguindo o sistema (`view/compose/TGComposeTheme.kt`, acento verde-azulado).
- Layout no estilo Songsterr: sem barra no topo. A partitura começa sob a barra de status, com nome e artista como cabeçalho que rola junto (`TGSongView.paintHeader`).
- Barra flutuante de menu acima do player (`view/main/TGMainMenuBar.kt`): ☰ menu lateral, seletor de faixa, afinador, modo atual (menu com os nomes) e ⋮. Ela some enquanto a partitura rola e quando as opções de prática estão abertas.
- Três modos (`view/practice/TGAppModeController.kt`): Play (padrão), Practice e Edit. Salvar, teclado de tablatura e menus de edição só no modo Edit.
- Afinador (`fragment/impl/TGTunerFragment.kt`, detector YIN em `view/tuner/TGPitchDetector.java`): usa as cordas da faixa atual e toca a nota de referência.
- Barra de baixo arrastável com 3 alturas (`view/practice/TGBottomBars.kt`):
  - Play: play/stop, BPM, loop.
  - Practice: BPM com −5/+5, rampa de treino em BPM (de/até/incremento, contador de voltas), loop por compasso, metrônomo e count-in.
  - As configurações de prática ficam em `TGPracticeSession`/`TGPracticePlayer`.
- Loop pela tablatura: no modo Practice, toque longo + arrastar sobre os compassos define o trecho (`TGSongViewGestureDetector`).
- Afinação (nomes das notas) ao lado da primeira pauta de tablatura (`TGLayout.DISPLAY_TUNING`).
- Em Compose:
  - barra flutuante, menu ⋮ (bottom sheet por níveis), menu lateral com recentes e faixas, configurações;
  - tela de Instrumentos (mute/solo por faixa, volume por canal);
  - diálogos de edição (base `view/dialog/fragment/TGComposeModalFragment.kt`, componentes em `view/compose/TGForm.kt`).
- Ainda em Views/XML:
  - teclado de tablatura (modo Edit);
  - itens dos menus de contexto (`res/menu/*.xml`, mostrados dentro do sheet em Compose);
  - navegador de arquivos interno (opcional);
  - popups de escolha, confirmação e mensagem (AlertDialog padrão, mantidos de propósito);
  - editores de pontos de bend e tremolo bar (Views embutidas nos diálogos Compose).
- A tablatura é desenhada em Canvas pelo motor do TuxGuitar e não migra para Compose.
- Validação de UI: usar um agente revisor de UI/UX antes de fechar telas novas.
- A verificar: issue aberta #1146 do upstream (repetições em modo loop).

## Próximos passos
- Afinador: validar no aparelho as cordas graves (Drop C, 7 cordas) sem salto de oitava.
- Teclado de tablatura em Compose, se a edição pelo celular for muito usada.
- Remover a action bar legada (hoje sempre escondida, só o navegador de arquivos opcional a mostra) e o `TGMainMenu`.
- Count-in de mais de 1 compasso.

## Regras
- Mudanças em branches, commits pequenos, nada no master do upstream.
- Se algo não puder ser verificado (ex.: build), dizer explicitamente em vez de supor.
