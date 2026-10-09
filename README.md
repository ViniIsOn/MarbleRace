# MarbleLab Studio — v1.2 beta 🎱

Jogo original de competições animadas no Android, sem SDK de anúncios, feito em Java e pensado para vídeos verticais (Shorts). O repositório continua chamado **MarbleRace**; o applicationId permanece `com.viniison.marblerace` para permitir atualizar a instalação existente.

## Modos da v1.2

| Modo | Descrição |
|---|---|
| Anel de Bolinhas | Aro circular preto, céu azul, bolas coloridas, contagem regressiva e saída que gira. |
| Eliminação | Arena circular diminui progressivamente, até haver um vencedor. |
| Destruir o Núcleo | Bolinhas rebatem num núcleo central; vence quem acerta mais. |
| Corrida de Minhocas | Competidores com corpos de bolinhas cruzam uma pista em velocidade. |
| Bicicletas | Disputa lateral com rodas, rampas, saltos e obstáculos. |
| Corrida Clássica | Quatro pistas preexistentes: Sky Drop, Candy Lab, Neon Reactor e Volcano Rush. |

**Atenção:** são seis opções de jogo, não uma reprodução de todo o catálogo do criador MIKAN. A inspiração é o gênero de competições visuais, com artes e código próprios.

## Imagens de personagens

No editor do corredor:
1. Toque em **Buscar PNG / GIF online** e no atalho **GOOGLE ↗**.
2. Digite o nome do personagem (por exemplo, Mario ou Blu).
3. Segure a imagem para tentar importar pelo visualizador do Google.
4. Se a miniatura for bloqueada ou de baixa qualidade, toque **Abrir no navegador**, salve uma imagem permitida e volte ao jogo para importá-la pela galeria.
5. No novo recortador, **arraste e aproxime com dois dedos** para enquadrar o rosto na bolinha.

A interface do Google pode restringir o uso em navegador embutido; por isso o fluxo alternativo no navegador foi incluído. Nenhuma pesquisa funciona como uma API oficial de Google Images. A busca integrada complementar usa Wikipedia e Wikimedia Commons. Imagens de personagens, fotos e logos podem ter direitos autorais: confira licenças e permissões antes de publicar.

## Vídeos e APK

- Exportação MP4 vertical: 720 × 1280, 30 FPS, sem áudio. Adicione música no editor.
- Abre em Android 8+, mas exportação MP4 interna requer Android 10+.
- A versão é **beta**; compatibilidade da exportação depende do codificador H.264 do aparelho.
- Para instalar pelo celular: abra [GitHub Actions](https://github.com/ViniIsOn/MarbleRace/actions), escolha a compilação verde **Build Android APK**, baixe o artefato `MarbleRace-debug-APK`, extraia o ZIP e instale `app-debug.apk`.
- Se a compilação falhar, envie o link da execução com o ❌ para que o log seja investigado.

Projeto sem anúncios próprios nem bibliotecas de rastreamento; o Google Images e outros sites externos podem exibir conteúdo ou anúncios próprios.
