#!/usr/bin/env bash
# ==============================================================================
# DmC: Devil May Cry - Native Recompiled Game Launcher (ReXGlue)
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/out/build/linux"
DEFAULT_GAME_ROOT="${SCRIPT_DIR}/../game"

# 1. Locate game data root
GAME_DATA_ROOT=""
for arg in "$@"; do
    if [[ "$arg" == --game_data_root=* ]]; then
        GAME_DATA_ROOT="${arg#*=}"
    fi
done

if [[ -z "$GAME_DATA_ROOT" ]]; then
    if [[ -d "$DEFAULT_GAME_ROOT" ]]; then
        GAME_DATA_ROOT="$DEFAULT_GAME_ROOT"
    elif [[ -d "${SCRIPT_DIR}/game" ]]; then
        GAME_DATA_ROOT="${SCRIPT_DIR}/game"
    else
        echo "======================================================================"
        echo "AVISO: Nenhum diretório de dados do jogo especificado."
        echo "Uso: ./run.sh --game_data_root \"/caminho/para/dados_extraidos\""
        echo "======================================================================"
        exit 1
    fi
fi

# 2. Check executable
EXECUTABLE="${BUILD_DIR}/dmc"
if [[ ! -f "$EXECUTABLE" ]]; then
    echo "Executável dmc não encontrado em ${BUILD_DIR}."
    echo "Por favor, compile antes de executar: ninja -C '${BUILD_DIR}' dmc"
    exit 1
fi

# 3. Create required directories
mkdir -p "${SCRIPT_DIR}/cache"
mkdir -p "${SCRIPT_DIR}/user_data"

# Auto-copy default dmc.toml configuration if missing in build directory
if [[ ! -f "${BUILD_DIR}/dmc.toml" && -f "${SCRIPT_DIR}/dmc.toml" ]]; then
    cp "${SCRIPT_DIR}/dmc.toml" "${BUILD_DIR}/dmc.toml"
fi

# 4. Export runtime environment
SDK_LIB_DIR="${SCRIPT_DIR}/../rexglue-sdk/out/linux-amd64"
if [[ ! -d "$SDK_LIB_DIR" && -d "${SCRIPT_DIR}/../sdk/out/linux-amd64" ]]; then
    SDK_LIB_DIR="${SCRIPT_DIR}/../sdk/out/linux-amd64"
fi
export LD_LIBRARY_PATH="${BUILD_DIR}:${SDK_LIB_DIR}:${LD_LIBRARY_PATH}"
export DISPLAY="${DISPLAY:-:0}"

echo "======================================================================"
echo "Iniciando DmC: Devil May Cry (Nativo Recompilado - ReXGlue)"
echo "Dados do Jogo: ${GAME_DATA_ROOT}"
echo "Executável:    ${EXECUTABLE}"
echo "======================================================================"

cd "${BUILD_DIR}"
exec "${EXECUTABLE}" \
    --game_data_root="${GAME_DATA_ROOT}" \
    --user_data_root="${SCRIPT_DIR}/user_data" \
    --cache_root="${SCRIPT_DIR}/cache" \
    --gpu_plugin=xenos \
    --render_target_path_vulkan=fsi \
    --gpu_allow_invalid_fetch_constants=true \
    "$@"
