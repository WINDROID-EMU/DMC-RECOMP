# Guia: shaders de jogos Unreal Engine 3 (Xbox 360) no Vulkan/Adreno com XenosRecomp

Jogo de referência: DmC: Devil May Cry (2013) · Preparado em 24/10/2026

## 1. Premissa e escopo

Você escreveu "unity3". Assumi **Unreal Engine 3 (UE3)**: o DmC (2013, Ninja Theory) usa UE3 e saiu para Xbox 360, enquanto a Unity nunca teve runtime oficial de Xbox 360. Se você quis dizer outra engine, o roteiro muda e vale ajustar o documento.

Este guia cobre apenas o lado de **GPU e shaders**: pegar os shaders da GPU Xenos, convertê-los com o XenosRecomp e rodá-los em Vulkan nas GPUs Adreno. A parte de CPU (PowerPC para C++ pelo ReXGlue/XenonRecomp), áudio e kernel ficam fora.

**Nível de confiança.** O que descreve o XenosRecomp vem do README oficial do hedge-dev. O que é específico de UE3, do DmC e do Adreno é conhecimento geral meu, sem checagem no jogo ou no aparelho, e está marcado com **\[verificar\]**.

## 2. Visão geral do pipeline

1. Compilar o XenosRecomp e ter o DXC instalado.
2. Extrair os binários de shader do jogo (do executável, dos pacotes UPK ou capturados em tempo de execução).
3. Rodar o XenosRecomp: shader Xenos para HLSL, HLSL para SPIR-V via DXC.
4. Adaptar o recompilador às necessidades do jogo (vertex fetch, constantes, texturas, instruções faltantes).
5. Implementar a camada de renderização Vulkan (plume) que intercepta a API D3D do Xbox 360 e usa os shaders recompilados.
6. Ajustar para a arquitetura do Adreno (GPU em tiles, precisão, limites de driver).
7. Integrar ao runtime (ReXGlue): lookup por hash, cache de pipelines, remoção do plugin Xenos.
8. Validar shader por shader e corrigir.

## 3. Ferramentas necessárias

| Ferramenta | Para quê |
| --- | --- |
| XenosRecomp (hedge-dev) | Converter shader Xenos em HLSL e gerar cache DXIL/SPIR-V |
| CMake 3.20+ e Clang (C++17) | Compilar o XenosRecomp; o autor só testou com Clang |
| DirectX Shader Compiler (DXC) | HLSL para SPIR-V (e DXIL, se precisar) |
| spirv-val / spirv-opt / spirv-cross | Validar, otimizar e inspecionar SPIR-V |
| Ghidra | Análise do executável (já usado nos seus outros projetos) |
| RenderDoc (Android) | Depurar frames no Adreno \[verificar suporte ao seu aparelho\] |
| Xenia Canary | Renderer de referência para comparar imagens |

Clone recursivo é obrigatório (há submódulos):

```bash
git clone --recursive https://github.com/hedge-dev/XenosRecomp.git
cd XenosRecomp
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++
cmake --build build -j
```

O XenosRecomp é uma ferramenta de build que roda no PC. No Android só entram os shaders já recompilados.

## 4. Extrair os shaders do jogo

O XenosRecomp não extrai nada: ele recebe binários de shader prontos. Nos dois modos de uso (arquivo único ou pasta inteira) o comando é:

```bash
XenosRecomp [entrada] [saida] [shader_common.h]
```

Por arquivo, a saída é um `.hlsl`. Por pasta, a saída é um `.cpp` com o cache DXIL/SPIR-V embutido. O modo pasta é pensado para jogos com shaders no executável ou em arquivos não comprimidos.

**Em UE3** os shaders costumam estar em *shader caches* dentro de pacotes `.upk` **\[verificar nos arquivos do DmC\]**. Dois problemas prováveis:

- **Pacotes comprimidos** (no Xbox 360 a UE3 usa LZX, entre outros) **\[verificar\]**: é preciso descomprimir antes de varrer a pasta.
- **Container de cache próprio da UE3**: os blobs de microcódigo ficam dentro de estruturas serializadas da engine, e o scanner do XenosRecomp procura binários de shader válidos, não entende formatos de pacote. Pode ser necessário um extrator que separe cada shader em um arquivo.

**Alternativa mais robusta: capturar em tempo de execução.** Como o ReXGlue já passa por todas as chamadas D3D do jogo, dá para registrar cada shader quando o jogo o cria (vertex e pixel), salvar o blob e rodar o XenosRecomp sobre essa pasta. Assim você cobre também shaders que não estão no pacote, e o hash usado no lookup vem do mesmo blob que o jogo entrega.

## 5. Adaptar o XenosRecomp ao jogo

O autor avisa que o recompilador não funciona de primeira e que as partes específicas do Sonic Unleashed ficam sob a macro `UNLEASHED_RECOMP`. Para a UE3 você vai tratar cada item abaixo.

### 5.1 Constantes

- Vertex shader: 256 registradores `float4` (4096 bytes). Pixel shader: 224 (3584 bytes). São copiados direto do dispositivo de render do guest, em little-endian.
- Os buffers são preenchidos a partir dos **dados de reflexão embutidos no shader**. Sem esses dados o recompilador não funciona; a saída é declarar um array `float4` cobrindo todo o intervalo. Verifique se os shaders do DmC trazem a tabela de constantes.
- **Constantes inteiras não estão implementadas.** Shaders com loops usam essas constantes; confira se os do jogo precisam.
- Constantes booleanas: 16 por estágio, empacotadas num inteiro de 32 bits. O hardware suporta até 128, então talvez precise ampliar `g_Booleans`.
- O buffer "compartilhado" é específico do Unleashed Recompiled e precisa ser redesenhado para o seu runtime.
- **Skinning**: personagens usam matrizes de ossos em registradores de constantes, com indexação dinâmica. O README avisa que podem ocorrer problemas com indexação dinâmica de constantes em vários operandos; teste os shaders de personagem cedo **\[verificar\]**.

### 5.2 Vertex fetch

- O Unleashed Recompiled converte as declarações de vértice em entradas nativas do Vulkan, dispensando permutações de shader por declaração.
- Os dados de vértice sofrem endian swap em palavras de 32 bits, o que embaralha formatos de 16 bits (viram YXWZ) e exige o `g_SwappedTexcoords`. Isso vale para `TEXCOORD`; outras semânticas podem precisar do mesmo tratamento.
- `R11G11B10` não existe em hardware desktop (e provavelmente nem no Adreno): o recompilador desempacota manualmente em `NORMAL`, `TANGENT` e `BINORMAL` via specialization constant.
- Algumas semânticas são forçadas a `uint4` por shader. Isso é manual por jogo.
- **Locations do Vulkan estão fixas** para o Sonic Unleashed (limite de 16). A solução genérica é atribuir locations por vertex shader e criar as declarações em tempo de execução.
- Instanciamento é feito à mão no Xbox 360 e é totalmente específico do jogo. Veja se o DmC usa instanciamento e como.
- *Mini vertex fetch* e bindings de vertex fetch não estão implementados.

### 5.3 Texturas e samplers

- Abordagem *bindless*, com índices de descritor no buffer compartilhado e um índice por tipo de textura. Texturas 1D não estão implementadas, mas são fáceis de adicionar.
- Não implementados: LOD explícito e filtros de sampler no fetch. Só o offset em pixels é suportado (usado em shadow mapping).
- Cubemaps: a instrução `cube` é tratada guardando direções em um array local que o DXC costuma otimizar; em shaders de fluxo complexo isso pode falhar.
- Alguns tipos de sampler do Xbox 360 podem não existir em hardware desktop e exigem implementação própria.
- **Sombras** na UE3 costumam depender de fetch com offset e filtragem manual **\[verificar\]**.

### 5.4 Controle de fluxo e instruções

- Como o HLSL não tem `goto`, o controle de fluxo vira um `while` com `switch` sobre um `pc` local. O autor diz que isso não foi bem testado (poucos shaders complexos no Sonic Unleashed). Shaders de material da UE3 são mais variados, então espere achar bugs aqui.
- Shaders simples podem ser "achatados", o que ajuda o DXC a otimizar.
- Instruções que resultam em `INF`/`NaN` podem se comportar diferente; a maioria é limitada a `FLT_MAX`.
- Indexação dinâmica de registradores não está implementada (sugestão do autor: registradores como array).
- Não implementados: **memory export**, **point size**. Se partículas ou efeitos da UE3 usarem memexport, vão precisar de solução própria **\[verificar\]**.

### 5.5 Specialization constants

- Flag de formato `R11G11B10` e flag de **alpha test** (hardware moderno não tem alpha test fixo; o recompilador insere uma comparação "menor que o limite" no fim do pixel shader). Podem faltar outros tipos de comparação.
- No SPIR-V isso é nativo. No DXIL é preciso linkar bibliotecas, mas como seu alvo é Vulkan, basta ficar com SPIR-V.

## 6. Do HLSL ao SPIR-V

- Compile com o DXC mirando Vulkan (`-spirv`, com ambiente alvo Vulkan 1.1 ou 1.2 conforme o driver). Valide cada saída com `spirv-val`.
- O cache do XenosRecomp comprime SPIR-V com smol-v e zstd. Mantenha ou troque conforme seu empacotamento no APK.
- **Atenção a `vk::RawBufferLoad`.** No Vulkan, o XenosRecomp passa os endereços GPU dos buffers de constantes por push constants e lê com `vk::RawBufferLoad`. Isso exige *buffer device address*. O suporte a essa extensão no seu Adreno e no seu driver Android precisa ser conferido **\[verificar com vulkaninfo\]**. Se faltar, o plano B é trocar essas leituras por UBO/SSBO com descritores normais e adaptar as macros de acesso.
- Cheque também o limite de push constants do aparelho (normalmente 128 bytes).

## 7. Camada de renderização (plume) e API do Xbox 360

O objetivo é substituir a emulação da GPU Xenos por uma tradução de alto nível da API D3D do jogo, como no Unleashed Recompiled. Itens típicos:

- **EDRAM e resolves**: o Xbox 360 renderiza em memória embutida e "resolve" para textura. Em Vulkan, mapeie isso para render passes/attachments normais e trate cada resolve como uma cópia ou fim de render pass.
- **Predicated tiling**: a UE3 em 360 pode dividir a tela em tiles quando o framebuffer não cabe na EDRAM. No port, trate como um único render pass **\[verificar\]**.
- **Formatos**: depth `D24FS8`, formatos de cor especiais (inclusive `7e3` float de 10 bits, usado para HDR no 360) e formatos 2\_10\_10\_10 precisam de equivalentes ou conversão **\[verificar quais o DmC usa\]**.
- **Texturas**: estão *tiled* e em big-endian no guest. É preciso desfazer o tiling e o swap antes de subir para a GPU, ou fazer isso em compute.
- **Estados de render, blend, depth/stencil, culling**: mapeamento um a um para estados do Vulkan.
- **Gamma/sRGB**: confira em qual etapa o jogo aplica gamma, para não duplicar.

## 8. Particularidades do Adreno

- **GPU em tiles (TBDR)**: prefira poucos render passes, `loadOp`/`storeOp` corretos (use `DONT_CARE` e `CLEAR` sempre que possível), e evite cópias e resolves desnecessários. Um "resolve" por efeito de pós-processamento pode custar caro.
- **Precisão**: force `highp` onde o shader do Xbox 360 assume float32. Tratamento diferente de denormais, `NaN` e `INF` no driver pode produzir artefatos que não aparecem em PC.
- **Descritores bindless**: confira `descriptorIndexing` e os limites de samplers/imagens por estágio **\[verificar\]**.
- **Compressão de textura**: confirme se o aparelho expõe BCn (`textureCompressionBC`); se não, transcode para ASTC ou RGBA8 **\[verificar\]**.
- **Compilação de pipelines**: pipelines compilados em tempo real causam engasgos. Use pipeline cache em disco, crie pipelines em threads separadas e pré-aqueça os mais comuns.
- **Bugs de driver**: shaders grandes com muito controle de fluxo costumam ser os que quebram no driver proprietário; mantenha o modo "achatar" ligado sempre que possível.
- **Memória**: textura e vertex data do jogo passam por conversão; planeje o uso de memória do dispositivo.

## 9. Específico da UE3

- **Permutações de material**: a UE3 gera muitos shaders (material × vertex factory × iluminação). O inventário será grande; automatize.
- **Shaders globais**: pós-processamento, resolves e passes de sombra têm shaders próprios e costumam ser os mais críticos para a imagem final.
- **Skinning e vertex factories**: veja a seção 5.1.
- **Inventário de recursos**: antes de implementar qualquer coisa do item 5, rode o XenosRecomp sobre todos os shaders e gere uma lista de quais instruções, tipos de fetch e formatos realmente aparecem. Implemente primeiro o que o jogo usa de fato. É a mesma lógica do autor: ele implementou só o necessário para o Sonic Unleashed.

## 10. Integração com o runtime

- No lookup em tempo de execução, o shader é mapeado para a versão recompilada por **hash XXH3 de 64 bits** do blob original.
- Defina o que acontece com um shader sem versão recompilada (log + shader de fallback visível, para achar rápido).
- Decida se o plugin GPU Xenos será mantido como fallback ou removido depois que o render nativo estiver estável.

## 11. Validação

1. `spirv-val` em todos os shaders gerados.
2. Compare o mesmo frame com o Xenia Canary e com o seu render (captura no RenderDoc).
3. Teste por categoria: UI, personagem (skinning), cenário estático, partículas, sombras, pós-processamento.
4. Sintomas comuns e suspeitas:
   - imagem de cabeça para baixo ou espelhada: coordenadas/viewport;
   - tudo preto: constantes ou texturas não ligadas;
   - geometria explodindo: vertex fetch ou endian swap;
   - cores estranhas: formato de textura/gamma;
   - piscadas: `NaN` ou precisão no Adreno.

## 12. Checklist

- [ ] Confirmar a engine do jogo (UE3) e a versão do executável
- [ ] Compilar XenosRecomp e instalar DXC
- [ ] Extrair ou capturar todos os shaders
- [ ] Inventário de instruções, fetches e formatos usados
- [ ] Confirmar dados de reflexão de constantes nos shaders
- [ ] Implementar constantes inteiras e booleanas se usadas
- [ ] Generalizar vertex fetch e locations
- [ ] Tratar texturas (LOD, filtros, cubemap, 1D) conforme o inventário
- [ ] Gerar SPIR-V e validar
- [ ] Verificar buffer device address no Adreno ou trocar por UBO/SSBO
- [ ] Camada plume: resolves, formatos, estados
- [ ] Pipeline cache e compilação assíncrona
- [ ] Lookup por hash no runtime e fallback
- [ ] Comparação visual com o Xenia

## 13. O que não verifiquei

- Quais formatos de pacote, cache de shader e compressão o DmC usa de fato.
- Se os shaders do DmC trazem dados de reflexão de constantes.
- Suporte a buffer device address, BCn e indexação de descritores no seu Adreno.
- Se o DmC usa memexport, instanciamento, formato `7e3` ou predicated tiling.

Esses pontos só se confirmam com os arquivos do jogo e o aparelho em mãos.
