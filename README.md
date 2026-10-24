# DmC: Devil May Cry - Native Recompilation (ReXGlue)

Port nativo e recompilação estática de **DmC: Devil May Cry** (Xbox 360 / XEX2) para Linux (x86_64) e Android (ARM64-v8a) utilizando o runtime **ReXGlue** e backend gráfico **Vulkan**.

---

## 🎮 Funcionalidades

- **Recompilação Estática Completa**: Código PowerPC remapeado e compilado nativamente para código de máquina do host com suporte a C++23.
- **Resolução de Funções Guest**: Registro completo de rotinas internas, serializadores de checkpoints (`0x82611A98`, `0x82484AA0`), vtables polimórficas (`0x83433710`, `0x83433718`) e rotinas de sistema.
- **Renderização Vulkan com FSI (Fragment Shader Interlock)**: Suporte preciso a formatos customizados de render-targets do Unreal Engine 3 na EDRAM do Xbox 360 (`k_16_16_16_16`, `k_2_10_10_10`, `kD24FS8`), eliminando artefatos e tintas avermelhadas.
- **Compilação Assíncrona de Shaders**: Otimizações no `dmc.toml` para geração paralela de pipelines sem travamentos na apresentação de quadros (`vulkan_async_skip_incomplete_frames = false`, `vulkan_pipeline_creation_threads = 6`).
- **Áudio e Controles Nativos**: Suporte completo a gamepads e áudio de baixa latência através do SDL3.
- **Estabilidade POSIX**: Tratamento aprimorado de memória compartilhada (`shm_unlink`), tratamento de `SIGBUS` e exclusão mútua recursiva no subsistema de entrada.

---

## 🛠️ Requisitos de Sistema (Linux)

- **SO**: Linux x86_64 (Ubuntu 22.04+, Arch Linux, Fedora 38+, etc.)
- **Compilador**: GCC 13+ ou Clang 16+ com suporte completo a **C++23**
- **CMake**: Versão 3.25 ou superior
- **Gerador de Build**: Ninja
- **Vulkan**: Driver compatível com Vulkan 1.3 e extensão `VK_EXT_fragment_shader_interlock` (NVIDIA driver 510+, Mesa 23+ RADV/ANV).
- **Dependências de Desenvolvimento**:
  ```bash
  sudo apt install build-essential cmake ninja-build libvulkan-dev libsdl3-dev
  ```

---

## 🚀 Como Compilar e Executar no Linux

### 1. Compilar o SDK (ReXGlue SDK)
```bash
cmake -B rexglue-sdk/out/linux-amd64 -S rexglue-sdk -G Ninja -DCMAKE_BUILD_TYPE=Release
ninja -C rexglue-sdk/out/linux-amd64
```

### 2. Compilar o Executável do Jogo (`dmc`)
```bash
cmake -B recomp_project/out/build/linux -S recomp_project -G Ninja -DCMAKE_BUILD_TYPE=Release
ninja -C recomp_project/out/build/linux dmc
```

### 3. Executar o Jogo
Você pode executar diretamente via script inicializador apontando para a pasta com os dados extraídos do jogo:
```bash
./run.sh --game_data_root "/caminho/para/pasta_extraida_do_jogo"
```
*Se a pasta do jogo extraído estiver localizada em `./game` na raiz do projeto, você pode simplesmente rodar:*
```bash
./run.sh
```

---

## 📱 Compilação para Android (ARM64-v8a)

O projeto inclui o suporte completo para compilação Android dentro do diretório `android/`:
- **Android SDK**: API 26 (mínimo) a API 35 (alvo)
- **Android NDK**: `28.2.13676358` (ou versão recente compatível com C++23 e alinhamento de página 16KB)

Para compilar via linha de comando:
```bash
cd android
./gradlew assembleRelease
```
O APK gerado estará em:
```text
android/app/build/outputs/apk/release/app-release.apk
```

---

## 📂 Estrutura do Repositório

```text
├── android/            # Projeto Android (Gradle, JNI, CMake e recursos)
├── recomp_project/     # Projeto de recompilação do DmC (manifesto, código C++ gerado, toml)
│   ├── generated/      # Código C++ recompilado do PowerPC do jogo
│   ├── src/            # Código fonte host e ponto de entrada (main.cpp)
│   ├── default_functions.toml  # Mapeamento de funções e vtables do executável
│   ├── dmc.toml        # Configuração de renderizador Vulkan e anti-stutter
│   └── run.sh          # Script de execução
├── rexglue-sdk/        # Runtime estático ReXGlue e bibliotecas de compatibilidade
├── run.sh              # Link simbólico para iniciar o jogo rapidamente
└── README.md
```

---

## ⚖️ Avisos Legais e Isenção de Responsabilidade

Este projeto é uma ferramenta de recompilação e execução nativa para fins de preservação e engenharia reversa. **Nenhum arquivo proprietário ou recurso protegido por direitos autorais (ROMs, ISOs, texturas, modelos, áudio ou executáveis originais) do jogo está incluído neste repositório.** Os usuários devem fornecer seus próprios arquivos de jogo legalmente adquiridos.

DmC: Devil May Cry é marca registrada de Capcom Co., Ltd. e Ninja Theory.
