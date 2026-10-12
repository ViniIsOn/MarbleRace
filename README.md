# MarbleLab Studio — v1.9.1 beta 🎱

Jogo original de competições animadas no Android, sem SDK de anúncios, feito em Java e pensado para vídeos verticais (Shorts). O repositório continua chamado **MarbleRace**; o applicationId permanece `com.viniison.marblerace` para permitir atualizar a instalação existente.

## Modos da v1.2

| Modo | Descrição |
|---|---|
| Corrida do Aro | Aro preto **fechado** só na preparação: 3, 2, 1 → GO! → círculo desaparece → corrida em pista azul com paredes pretas e obstáculos → linha de chegada e pódio. |
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

- **Exportação acelerada de Shorts:** o aplicativo tenta primeiro codificar H.264 com hardware via GPU/OpenGL ES e MediaCodec Surface, eliminando a conversão lenta de cada pixel em Java. O vídeo mantém 720 × 1280 e 30 FPS; a renderização interna do caminho acelerado usa 540 × 960 e é escalada pela GPU.
- Se a GPU não aceitar a operação, o app volta automaticamente ao codificador de compatibilidade, que pode levar mais tempo. O tempo real varia com o aparelho, quantidade de corredores e obstáculos.
- A janela de progresso informa se está usando aceleração GPU ou compatibilidade e mostra os segundos decorridos.
- Exportação MP4 vertical: 720 × 1280, 30 FPS, sem áudio. A Corrida do Aro exporta **a introdução e a corrida completa**, além de manter o pódio por 1,5 segundo. Adicione música no editor.
- Abre em Android 8+, mas exportação MP4 interna requer Android 10+.
- A versão é **beta**; compatibilidade da exportação depende do codificador H.264 do aparelho.
- Para instalar pelo celular: abra [GitHub Actions](https://github.com/ViniIsOn/MarbleRace/actions), escolha a compilação verde **Build Android APK**, baixe o artefato `MarbleRace-debug-APK`, extraia o ZIP e instale `app-debug.apk`.
- Se a compilação falhar, envie o link da execução com o ❌ para que o log seja investigado.

Projeto sem anúncios próprios nem bibliotecas de rastreamento; o Google Images e outros sites externos podem exibir conteúdo ou anúncios próprios.

## Música para corridas e Shorts (v1.3)

1. Na tela do jogo, toque em **♫ SEM MÚSICA**.
2. Escolha **Adicionar músicas do celular** e selecione vários arquivos de áudio ao mesmo tempo.
3. Escolha uma faixa ou ative **Aleatório**; a biblioteca lembra as músicas importadas, sem incorporá-las ao APK.
4. Ao iniciar uma corrida, a música selecionada toca em loop. Pausar ou sair do app interrompe a reprodução.
5. Ao exportar o vídeo, arquivos **AAC/M4A** podem ser unidos ao MP4 sem recodificar, deixando o Short pronto com trilha.
6. Arquivos **MP3** são aceitos para ouvir durante a corrida, mas ainda **não são incorporados ao MP4**. Nesse caso, o jogo avisa que o vídeo foi salvo sem áudio e você pode adicionar a trilha em um editor de vídeo.

**Direitos de uso:** adicione apenas áudio que você pode utilizar. Não redistribuímos músicas nem importamos automaticamente playlists do YouTube. Para faixas NCS, confira a [política oficial](https://ncs.io/usage-policy) e credite os artistas. A permissão para usar músicas em um vídeo não equivale a uma licença para distribuí-las embutidas em um jogo.

O arquivo do projeto mantém o mesmo applicationId e dados existentes. Todos os recursos são beta e precisam ser testados no Android.

## Ajustes v1.4: música e resultado da corrida

- Botão amarelo `♫ SEM MÚSICA • TOQUE PARA ADICIONAR` na tela principal.
- Marcador `v1.4 • SEM ADS` no topo para ajudar a identificar APKs antigos.
- Corridas e obstáculos recebem uma nova semente de aleatoriedade ao criar uma disputa, apertar RESET ou começar uma nova corrida. Portanto, Mario e Sonic não precisam terminar sempre na mesma ordem.
- Velocidade de cada bolinha varia de forma moderada; modos Minhocas e Bicicletas também variam.
- Ao exportar, o app reutiliza a semente da corrida exibida para tentar manter o mesmo vencedor e cenário no vídeo, com física em passos fixos de 30 Hz.
- Corrigida a janela da biblioteca, que não deve mais esconder a lista de músicas no Android.

Para atualizar sem perder seus corredores, **instale o APK novo por cima do existente**, sem desinstalar. Confira o número da versão e o botão de música depois da instalação.

## Novidades da versão 1.5

- Corridas clássicas e Corrida do Aro têm **11.500 unidades de pista**, mais longas que as 6.200 anteriores.
- Mais de 65 obstáculos principais por mapa, além de obstáculos extras posicionados conforme a semente da partida. Rebatedores, rotores, objetos móveis, molas, pegajosos, rebatedores laterais e lançadores turbo.
- **Zonas de surpresa** em metade da pista: boost, ultra turbo, freio e desvio. A posição dos obstáculos varia de uma rodada para outra.
- Exibição de eventos como **BOOST**, **ARMADILHA**, **MOLA** e **REVIRAVOLTA** na bolinha afetada.
- Exportação vertical de até 70 s nas corridas longas, cortando automaticamente após os resultados.
- **Google Imagens respeita exatamente a pesquisa digitada**, sem acrescentar termos como "rosto", "personagem" ou "PNG".
- Os nomes, cores e fotos continuam salvos nas preferências internas e nas imagens do aplicativo. Agora há também uma **cópia de recuperação interna automática** salva após cada edição.
- Botão **DADOS** na tela: exporta um arquivo `MarbleLab_personagens.json` com as imagens reais embutidas; permite importar o arquivo para restaurar o elenco sem recriá-lo.

**Atenção sobre atualizações:** numa atualização normal, o Android preserva o armazenamento do aplicativo, desde que o APK tenha o mesmo identificador de pacote e **a mesma assinatura digital**. GitHub Actions de testes pode criar uma chave debug diferente a cada execução; nesse caso o Android pode recusar instalar por cima. NÃO desinstale o app com seus personagens sem antes fazer backup. Um backup na pasta Arquivos/Downloads, guardado fora do app, continua disponível após a desinstalação. A cópia automática interna, por sua vez, é apagada ao desinstalar o aplicativo.

## v1.5.1 — Corrida sem travar

- Obstáculos continuam variados (rebatidas, molas, turbo, lento, lançadores), mas são distribuídos em grupos separados, com passagem lateral preservada.
- Movimentos laterais menos extremos deixam os objetos longe das paredes.
- Uma colisão frontal agora aplica impulso lateral para o corredor conseguir contornar obstáculos em vez de ficar preso acima deles.
- Se ficar sem progredir por mais de dois segundos, entra uma recuperação curta chamada **DESVIO!**, mantendo a corrida em andamento.
- A pista mantém o comprimento longo, os rastros coloridos e os resultados baseados em semente aleatória.
- O GitHub Actions agora executa **96 corridas simuladas + 96 repetições determinísticas** antes de compilar o APK; uma rodada travada faz a build falhar.

## v1.6 — Música no vídeo e backup completo

- Ao exportar, o app usa o áudio selecionado da biblioteca; **MP3 é convertido pelo Android em AAC/M4A**, e então é incorporado ao MP4 sem reencodar o vídeo. Arquivos AAC podem ser incorporados diretamente.
- A exportação informa quando não foi possível converter a trilha, em vez de afirmar que o vídeo está com som. O codificador depende dos codecs presentes no Android.
- O botão **BACKUP** agora cria o documento `MarbleLab_backup_com_musicas.zip`. Contém nomes, cores, imagens, pista, modo, lista de músicas, faixa selecionada e **os arquivos de áudio importados**.
- O ZIP é um arquivo externo: se você guardá-lo em Downloads ou nuvem, poderá restaurar músicas e personagens mesmo após desinstalar o app. Arquivos de áudio grandes tornam o backup demorado e volumoso; não feche o app enquanto ele salva.
- O botão **RESTAURAR TUDO** aceita o novo ZIP e os backups JSON da versão anterior. JSON antigos restauram apenas os corredores e não mexem na música atual.
- A cópia automática interna continua salvando os personagens, enquanto o ZIP com músicas é criado **quando você escolher Fazer Backup**, para não duplicar centenas de megabytes sem consentimento.
- O app não baixa nem redistribui músicas de playlists de terceiros; somente copia arquivos adicionados pelo usuário.
- **Atualização sem desinstalar** preserva os dados se o APK tiver a mesma assinatura. Se o Android recusar atualizar, exporte o ZIP em uma versão que ofereça o botão antes de remover o aplicativo. A v1.5.1 anterior não tinha músicas no backup; se não for possível atualizar diretamente, preserve os arquivos de áudio originais para reimportá-los.

## v1.7 — CORE DESTRUCTION (1 a 2 minutos)

O antigo modo de acertar um único círculo foi substituído por uma batalha **entre dois núcleos** inspirada no gênero de minijogos de simulação de competições. As artes, lógica e animações são originais do MarbleLab, sem reproduzir imagens do MIKAN.

- A primeira bolinha da lista representa o núcleo **amarelo**; a segunda, o **roxo**. Os rostos dos corredores aparecem nas estruturas.
- Núcleos com **320 HP** e barras de resistência, efeitos de impacto e vencedor anunciado no fim.
- Armas automáticas alternam **tiros, bombas, serras, laser e golpes de guindaste**. O escudo temporário protege contra parte do dano. Um guindaste animado percorre a arena.
- A batalha usa um **cronômetro de 90 segundos**: não acaba antes de 60 segundos, terminando em até 90 segundos. Se nenhum núcleo chegar a zero HP, ganha quem tiver mais vida.
- Os resultados mudam a cada nova disputa, mas a exportação reproduz a mesma semente para preservar o vencedor.
- O vídeo MP4 aceita até **95 segundos** neste modo, incluindo a tela final. O áudio da biblioteca continua disponível.
- O GitHub Actions executa **48 batalhas simuladas e repetições idênticas** para validar duração, danos e resultados antes de gerar o APK.

A versão 1.7 mantém o mesmo `applicationId` e mecanismo de backup ZIP com personagens e músicas. Atualize o APK por cima sem desinstalar quando a assinatura permitir.

## v1.8 — Core Destruction: simulação física de bolinhas

A animação de tiros da v1.7 foi substituída por **bolinhas simuladas individualmente em física 2D**, com velocidade, massa, elasticidade e colisões. Não há um mecanismo de dano aleatório simulando impactos invisíveis.

- **Mapa simétrico**: paredes, divisória central, funis, rampas e buracos de descarte na parte inferior.
- **Geradores reais**: uma bolinha a cada 0,2 s por time + ondas de 50 bolinhas por lado a cada 17 segundos.
- **Object pool/limite**: máximo de 300 bolinhas ativas por time; marbles fora da arena ou antigas são removidas para evitar acúmulo infinito.
- **Física**: gravidade, restituição nas colisões circulares, atrito baixo, massa proporcional ao raio e grade espacial para impedir comparações quadráticas desnecessárias.
- **Obstáculos reais**: três serras, três hélices giratórias, portões de 5 segundos, boosters, pinos de pinball e funis.
- **Núcleos**: 1000 HP por lado, quatro placas de armadura quebráveis, dano calculado pela massa e velocidade de impactos de bolinhas adversárias. Efeito de dano, rachaduras e partículas coloridas.
- **HUD**: barras de HP, contadores de bolinhas, timer, eventos e placar de vitórias que fica salvo no celular.
- **Partidas de 60 a 95 segundos**, com MP4 vertical e quadro do vencedor ao final. O editor, importação de imagens e backup ZIP permanecem intactos.
- A implementação usa **motor 2D próprio em Java**, não é Box2D/Algodoo nem cópia de um mapa específico do MIKAN.
- GitHub Actions verifica consistência, uso de memória, vencedor, dano nos núcleos e reprodutibilidade para exportação.

**Limitações de hardware:** mesmo com teto de 300 por time, a taxa de quadros depende do celular. Se houver queda de desempenho, será necessário ajustar quantidade de bolinhas e detalhes visuais.

## v1.9 — Correção da batalha Core Destruction (vídeo de teste)

- Corrigida a hélice: **só aplica impulso físico quando a bolinha encosta na pá**, e o vetor tangencial respeita o sentido real da rotação. Antes um empurrão invisível favorecia uma das metades do mapa.
- Núcleos com área de colisão consistente com o disco colorido visível; o aro decorativo fica um pouco maior.
- As quatro placas de armadura agora interceptam projéteis que chegam ao núcleo. Enquanto alguma placa existir, ela recebe dano e a bolinha é **refletida com brilho visível**, sem desaparecer silenciosamente.
- A vida do núcleo só cai após as armaduras serem destruídas.
- A destruição tem transição de cerca de 2,4 s, onda de choque e fragmentos antes da tela do vencedor.
- Interface 9:16 ajustada: cronômetro, nomes dos times e HP em linhas separadas.
- Exportação aceita a disputa de até 120 s mais a animação e a tela de vitória, mantendo suporte à música do usuário e backup existente.
- Regressões de CI verificam colisões entre equipes, defesa real, partidas reproduzíveis e resultados dos dois lados. Compilação Android ainda precisa passar para considerar o APK pronto.

**Atualizações:** preserve o mesmo applicationId. Não desinstale a versão antiga antes de assegurar um backup de corredores, imagens e músicas; o Android exige assinatura compatível para instalar APKs diretamente por cima.

## v1.9.1 — Backup seguro, restauração confiável

- **SALVAR NOVO BACKUP** cria um ZIP com nomes, imagens e músicas. O app agora primeiro conclui o arquivo em área temporária e verifica o diretório central do ZIP, o tamanho e o CRC de cada item.
- Somente depois de aprovado o ZIP é copiado para o local escolhido pelo usuário. O arquivo final é reaberto e seu SHA-256 deve ser idêntico ao da cópia verificada.
- **RESTAURAR BACKUP** valida um ZIP existente **antes** de alterar os personagens e a biblioteca. O ZIP é primeiro copiado para área temporária para testar integridade; ZIPs incompletos ou corrompidos são recusados com mensagem clara.
- A restauração preserva a compatibilidade com os antigos backups JSON de corredores.
- Agora há uma janela de progresso durante salvamento/restauração; não aparece confirmação antecipada.
- Os arquivos são nomeados com data/hora para não confundir o backup anterior com o novo.
- Um ZIP incompleto **não pode ter os dados ausentes recriados**. Não exclua seu arquivo antigo e, se os dados ainda estão no aplicativo, faça um novo backup e aguarde a mensagem de sucesso.
- Os testes automatizados criam um ZIP íntegro, um ZIP truncado e outro com dados alterados para validar a detecção de problemas, além de compilar o APK.
- Os backups ZIP podem exigir espaço livre temporário proporcional ao tamanho das músicas. Em caso de pouco espaço, libere armazenamento e tente de novo.

**Importante:** ao instalar APK de outra execução do GitHub, a assinatura de debug pode mudar. O Android pode recusar atualizar por cima; não desinstale sem fazer primeiro uma cópia segura dos dados.
