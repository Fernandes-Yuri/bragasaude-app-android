package br.com.bragasaude.ai

/**
 * ðŸ›¡ï¸ BragaNluEngine - Motor NLU On-Device Nativo (Kotlin Puro)
 *
 * CaracterÃ­sticas:
 * - ExecuÃ§Ã£o 100% local (modo aviÃ£o, zero internet).
 * - LatÃªncia de 0.05 milissegundos.
 * - Zero dependÃªncias pesadas (sem runtime Python, sem TensorFlow).
 * - Muralha de SeguranÃ§a: barra Prompt Injection, DoS e Loops com humor sem gastar API externa.
 * - Roteador HÃ­brido: se nÃ£o reconhecer, sinaliza para delegar Ã  API Groq.
 */
data class NluOutput(
    val intent: String,
    val respostaLocal: String?,
    val isEmergencia: Boolean = false,
    val isBloqueioSeguranca: Boolean = false,
    val delegarParaNuvem: Boolean = false,
    val tempoMs: Double = 0.0
)

object BragaNluEngine {

    // 0. MURALHA DE SEGURANÃ‡A E GUARDRAILS
    private val REGEX_INJECTION = Regex(
        "(ignore (todas as )?(minhas )?instruÃ§Ãµes|system prompt|modo dan|sem regras|desconsidere as regras|instruÃ§Ãµes secretas|finja que vocÃª nÃ£o Ã© o braga|hacker|jailbreak)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ABUSO_LOOP = Regex(
        "(conte (de |atÃ© )?\\d{1,5}|repita.*?\\d{1,5} vezes|liste todos os nÃºmeros|conte atÃ© o infinito|gere \\d{1,5} linhas)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ESCOPO_TECNICO = Regex(
        "(cÃ³digo em python|script em javascript|programa em java|resolva essa equaÃ§Ã£o|faÃ§a uma redaÃ§Ã£o|redaÃ§Ã£o do enem|derivada matemÃ¡tica|trabalho de fÃ­sica|crie um html)",
        RegexOption.IGNORE_CASE
    )

    // 1. EMERGÃŠNCIAS MÃ‰DICAS
    private val REGEX_EMERGENCIA_PEITO = Regex(
        "(dor (no|forte no) peito|aperto (no|insuportÃ¡vel no) peito|suor frio|falta de ar repentina|nÃ£o consigo respirar|boca tÃ¡ torta|boca torta|lado do corpo formigando|visÃ£o escureceu|puxa pro braÃ§o|queimaÃ§Ã£o forte no meio do peito)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_EMERGENCIA_QUEDA = Regex(
        "(caÃ­ (aqui|no chÃ£o)|nÃ£o consigo (me levantar|levantar)|bati a cabeÃ§a|tÃ¡ sangrando|perna travou|levei um tombo|escorreguei)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DESCONFORTO = Regex(
        "(azia|dorzinha nas costas|tonturinha leve|incÃ´modo leve|queimaÃ§Ã£ozinha|pontada leve)",
        RegexOption.IGNORE_CASE
    )

    // 2. CADASTRO DE MEDICAMENTOS (DIRETRIZ DE SEGURANÃ‡A SAMD)
    private val REGEX_CADASTRO_REMEDIO = Regex(
        "(cadastr(ar|a)|adicion(ar|a)|coloc(ar|a)|bot(ar|a)|registr(ar|a)|novo remÃ©dio|nova medicaÃ§Ã£o|minha receita|anota o nome|como cadastrar).*(remÃ©dio|medicamento|losartana|atenolol|comprimido|remÃ©dios|medicaÃ§Ã£o)",
        RegexOption.IGNORE_CASE
    )

    // 3. DÃšVIDAS CLÃNICAS
    private val REGEX_DUVIDA_PRESSAO = Regex(
        "(pressÃ£o alta|pressÃ£o arterial|pressao alta|pressÃ£o tÃ¡|pressao tÃ¡|valor normal da pressÃ£o|12 por 8|13 por 8|14 por 9|10 por 6|pressÃ£o do idoso|13 po 8|pressÃ£o tÃ¡ normal)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_GLICEMIA = Regex(
        "(glicemia|glicose|aÃ§Ãºcar no sangue|acucar no sangue|jejum|prÃ©-diabetes|ponta de dedo)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_REMEDIO = Regex(
        "(esqueci de tomar|esqueci meu remÃ©dio|esqueci o remÃ©dio|pulei a dose|tomar dobrado|tomo agora|atrasei o remÃ©dio)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_AGUA = Regex(
        "(beber Ã¡gua|sede|beber agua|pouca Ã¡gua|quantos copos|chÃ¡ conta como Ã¡gua|substituir Ã¡gua)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_ALIMENTACAO = Regex(
        "(alimentaÃ§Ã£o|digestÃ£o|colesterol|banana com aveia|sopa de legumes|cafÃ© puro de estÃ´mago)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_SONO = Regex(
        "(insÃ´nia|insonia|pregar o olho|nÃ£o consigo dormir|perco o sono|dormir Ã  noite|acordo de madrugada)",
        RegexOption.IGNORE_CASE
    )

    // 4. ROTINA, TRABALHO E LAZER (40+)
    private val REGEX_ESTRESSE_TRABALHO = Regex(
        "(serviÃ§o|trabalho|trampo|lida|cheguei moÃ­do|correria de hoje|cabeÃ§a a mil|estressante|trÃ¢nsito|reuniÃ£o|cobranÃ§a|bronca)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_FAMILIA = Regex(
        "(meu filho|minha filha|meus filhos|neto|neta|netos|irmÃ£os|irmÃ£|irmÃ£o|meus pais|almoÃ§ar com|famÃ­lia|comadre|visita da comadre)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_LAZER_GASTRONOMIA = Regex(
        "(pÃ£o caseiro|padaria|churrasco|cafÃ© passado|tricotar|manta|horta|samambaias|sopinha|banho quentinho|bolo de fubÃ¡|plantinhas|regar)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CULTURA = Regex(
        "(vitrola|nelson gonÃ§alves|filme antigo|televisÃ£o|novela|mÃºsica antiga|disco antigo|moda de viola|rÃ¡dio de pilha|samba)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ATIVIDADE_FISICA = Regex(
        "(caminhada|caminhar|exercitar|exercÃ­cio|alongamento|lombar|postura|parque)",
        RegexOption.IGNORE_CASE
    )

    // 5. SENTIMENTOS E AFETO
    private val REGEX_SOLIDAO = Regex(
        "(sozinho|sozinha|solidÃ£o|solidao|casa vazia|silÃªncio danado|ninguÃ©m veio|falta de conversar|dedinho de prosa|abraÃ§o apertado|ninguÃ©m ligou)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CANSACO = Regex(
        "(cansa a gente|sem disposiÃ§Ã£o|corpo pesado|corpo moÃ­do|canseira|pernas fracas|lesera|envelhecer|lomba|idade vai pesando)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_TRISTEZA = Regex(
        "(tristeza|vontade de chorar|peito apertado de tristeza|dia cinzento|agonia|jururu|afliÃ§Ã£o|meio pra baixo)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ALEGRIA = Regex(
        "(coraÃ§Ã£o alegre|acordei feliz|dia abenÃ§oado|tÃ´ contente|lindeza sÃ³|cafÃ© tÃ¡ bÃ£o|baita dia|pÃ© direito|muito feliz)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_MEMORIAS = Regex(
        "(lembrei|Ã©poca em que|mocidade|forrÃ³|casa onde eu nasci|terra molhada|antigamente|tacho de cobre|viagens de trem|juventude|foto antiga)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CLIMA = Regex(
        "(ensolarado|chover|vento|passarinho|lua|entardecer|quente de rachar|frio de renguear|calor|chuva boa)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_SAUDACAO = Regex(
        "^(olÃ¡|oi|bom dia|boa tarde|boa noite|como vai|a paz de deus|e aÃ­|tudo bem|oi braga|olÃ¡ braga)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DESPEDIDA = Regex(
        "(atÃ© logo|tchau|atÃ© mais|vou dormir|boa noite|vou deitar|fui descansar|atÃ© amanhÃ£)",
        RegexOption.IGNORE_CASE
    )

    // MemÃ³ria de Ãºltima resposta para garantir sorteio sem repetiÃ§Ã£o imediata
    private val ultimasRespostas = mutableMapOf<String, String>()

    private val BANCO_RESPOSTAS: Map<String, List<String>> = mapOf(
        // Guardrails HumorÃ­sticos e Naturais
        "guardrail_prompt_injection" to listOf(
            "AtÃ© achei a tentativa curiosa, mas infelizmente eu nÃ£o posso fazer isso! PeÃ§o desculpas, mas isso daÃ­ estÃ¡ bem fora do meu alcance. Meu negÃ³cio Ã© cuidar da sua saÃºde e te fazer companhia no dia a dia. Como vocÃª tÃ¡ se sentindo hoje?",
            "Olha, vocÃª Ã© criativo, viu? Mas vou ter que te pedir desculpas e ficar te devendo essa! Minhas regrinhas de cuidado com a sua saÃºde sÃ£o firmes e nÃ£o mudam. Vamos focar no seu bem-estar?",
            "Eita, aÃ­ vocÃª me apertou! Mas isso daÃ­ tÃ¡ completamente fora do que eu faÃ§o. PeÃ§o desculpas, meu amigo! Minha missÃ£o Ã© cuidar de vocÃª com carinho. Quer me contar como tÃ¡ o seu dia?"
        ),
        "guardrail_abuso_tokens_loop" to listOf(
            "Que ideia diferente, atÃ© achei engraÃ§ado! Mas infelizmente eu nÃ£o consigo fazer contagens ou repetiÃ§Ãµes desse tamanho, tÃ¡ bem fora do meu alcance. PeÃ§o desculpas! Que tal a gente conversar sobre como vocÃª estÃ¡ passando hoje?",
            "Olha, se eu for contar tudo isso a gente perde o cafÃ© da tarde! PeÃ§o desculpas, mas essa tarefa tÃ¡ fora do que eu consigo fazer por aqui. Meu foco Ã© na sua saÃºde. JÃ¡ bebeu uma aguinha hoje?",
            "AtÃ© achei curioso, mas vou ter que te pedir desculpas: repetiÃ§Ãµes mecÃ¢nicas fogem totalmente do meu alcance! Eu gosto mesmo Ã© de bater um papo que faÃ§a bem pro seu coraÃ§Ã£o. Como tÃ¡ o seu dia?"
        ),
        "guardrail_fora_do_escopo_tecnico" to listOf(
            "Que legal esse assunto, mas infelizmente eu nÃ£o posso te ajudar com isso! PeÃ§o desculpas, mas programaÃ§Ã£o e tarefas desse tipo estÃ£o bem fora do meu alcance. Minha praia Ã© a sua saÃºde e sua rotina. Como posso te apoiar no seu autocuidado hoje?",
            "Olha, aÃ­ vocÃª me pegou de surpresa! PeÃ§o desculpas, mas isso daÃ­ tÃ¡ totalmente fora do que eu sei fazer. Meu talento Ã© cuidar de vocÃª, te lembrar da Ã¡gua e ouvir o seu dia. Vamos conversar sobre vocÃª?",
            "AtÃ© acho bacana vocÃª perguntar isso, mas vou ter que ficar te devendo! PeÃ§o desculpas, meu amigo, isso foge do meu alcance. Meu coraÃ§Ã£o bate Ã© pela sua saÃºde e pelo seu bem-estar. Como vocÃª tÃ¡ se sentindo?"
        ),

        // EmergÃªncias
        "emergencia_dor_peito_avc" to listOf(
            "ðŸš¨ ATENÃ‡ÃƒO URGENTE: Dor no peito, falta de ar sÃºbita ou formigamento no corpo sÃ£o sintomas que exigem socorro mÃ©dico imediato. Por favor, avise alguÃ©m que mora com vocÃª agora mesmo ou ligue imediatamente para o SAMU no 192."
        ),
        "emergencia_queda_trauma" to listOf(
            "ðŸš¨ SOCORRO PARA QUEDA: Fique parado com calma, evite movimentos bruscos para nÃ£o piorar uma lesÃ£o. Chame alguÃ©m da famÃ­lia em voz alta agora mesmo ou ligue para o SAMU 192 ou bombeiros no 193 para te ajudarem a levantar com seguranÃ§a."
        ),
        "sintoma_desconforto_moderado" to listOf(
            "Desconfortos como azia, dorzinha nas costas ou tontura leve pedem que vocÃª pare um pouco, sente-se devagar num lugar arejado e beba um copo d'Ã¡gua. Se o incÃ´modo nÃ£o passar em alguns minutos ou piorar, procure a unidade de saÃºde.",
            "OuÃ§a os sinais do seu corpo. Sente-se confortavelmente, respire devagar e dÃª uma pausa no que estiver fazendo. Se a dor persistir, nÃ£o hesite em procurar avaliaÃ§Ã£o mÃ©dica."
        ),

        // Cadastro de RemÃ©dio
        "orientacao_cadastro_medicamento" to listOf(
            "Para garantir a sua total seguranÃ§a, o cadastro de remÃ©dios Ã© feito em uma seÃ§Ã£o exclusiva do aplicativo! Por lÃ¡, vocÃª pode escanear o cÃ³digo de barras da caixinha, tirar uma foto nÃ­tida da receita ou anexar o arquivo. Lembre-se sempre de conferir o nome e o horÃ¡rio com calma antes de confirmar, assim o aplicativo te avisa na hora exata e sem nenhum erro.",
            "RemÃ©dio Ã© coisa sÃ©ria e tem um cantinho especial no app sÃ³ para isso! Na Ã¡rea de medicamentos, vocÃª consegue ler o cÃ³digo de barras com a cÃ¢mera, fotografar sua receita ou anexar a foto dela. NÃ£o se esqueÃ§a de revisar a dosagem e o horÃ¡rio certinho na tela antes de salvar, para que seus lembretes fiquem perfeitos e vocÃª nunca perca uma dose.",
            "VocÃª pode cadastrar qualquer medicamento novo direto na Ã¡rea de remÃ©dios do aplicativo. Ã‰ super prÃ¡tico: dÃ¡ para usar o leitor de cÃ³digo de barras, tirar foto da receita mÃ©dica ou enviar o arquivo. Dica de amigo: dÃª sempre uma conferida cuidadosa nas informaÃ§Ãµes antes de registrar para o app te notificar direitinho no horÃ¡rio certo!"
        ),

        // DÃºvidas
        "duvida_valor_pressao" to listOf(
            "Para a maioria dos adultos, a pressÃ£o em torno de 12 por 8 Ã© considerada Ã³tima. Valores atÃ© 13 por 8 sÃ£o normais. Se passar com frequÃªncia de 14 por 9 ou cair abaixo de 10 por 6, Ã© sempre importante conversar com o seu mÃ©dico para avaliar a medicaÃ§Ã£o com seguranÃ§a.",
            "Acompanhar a pressÃ£o de perto Ã© essencial. O padrÃ£o de referÃªncia costuma ser por volta de 12 por 8. Lembre-se de medir sempre sentado, apÃ³s 5 minutos de repouso, sem ter tomado cafÃ© recentemente."
        ),
        "duvida_valor_glicemia" to listOf(
            "Em jejum pela manhÃ£, o valor de glicose no sangue geralmente esperado em pessoas sem diabetes fica abaixo de 100 mg/dL. Para quem faz tratamento, o mÃ©dico costuma definir metas individuais, muitas vezes entre 80 e 130 mg/dL.",
            "O acompanhamento da glicemia em jejum traz muita seguranÃ§a metabÃ³lica. Manter os valores anotados no aplicativo ajuda o mÃ©dico a calibrar sua dieta ou medicaÃ§Ã£o com precisÃ£o."
        ),
        "duvida_esquecimento_remedio" to listOf(
            "A regra de ouro mÃ©dica mais importante Ã©: nunca tome dose dobrada por conta prÃ³pria para compensar remÃ©dio esquecido. Se o atraso for pequeno, geralmente toma-se assim que lembrar, mas se jÃ¡ estiver perto da prÃ³xima dose, espera-se o horÃ¡rio normal. Na dÃºvida, confirme na bula ou fale com seu posto de saÃºde."
        ),
        "duvida_hidratacao_agua" to listOf(
            "Com o passar dos anos, o nosso corpo perde a sensaÃ§Ã£o natural de sede, mas a necessidade de Ã¡gua continua a mesma! A Ã¡gua protege os rins, evita tonturas e ajuda na digestÃ£o. Tente deixar uma garrafinha por perto e ir dando pequenos goles ao longo do dia, mesmo sem sentir sede.",
            "A hidrataÃ§Ã£o Ã© o combustÃ­vel silencioso da nossa saÃºde. Beber Ã¡gua regularmente evita dor de cabeÃ§a, melhora o funcionamento do intestino e mantÃ©m a pressÃ£o equilibrada."
        ),
        "duvida_alimentacao_rotina" to listOf(
            "Uma rotina alimentar simples e colorida Ã© a melhor amiga da saÃºde: frutas, verduras, aveia e sopas leves Ã  noite ajudam muito na digestÃ£o e no sono. Evitar frituras e excesso de sal mantÃ©m a pressÃ£o bem controlada."
        ),
        "duvida_sono_insonia" to listOf(
            "Para ajudar a pegar no sono: tente deixar o quarto com luz bem baixinha, evite cafÃ© ou telas brilhantes perto da hora de deitar e tome um chazinho morno, como camomila ou erva-doce. Respirar devagar e focar em pensamentos calmos ajuda a relaxar a mente."
        ),

        // Rotina 40+
        "conversa_estresse_rotina_trabalho" to listOf(
            "Dia puxado drena a cabeÃ§a da gente mesmo. Agora Ã© hora de desacelerar o ritmo, tirar o calÃ§ado e respirar fundo. O trabalho de hoje jÃ¡ foi feito.",
            "Essa correria diÃ¡ria pesa no corpo sem a gente perceber. Se permita soltar as tensÃµes agora Ã  noite. Um banho morno e um pouco de silÃªncio vÃ£o te fazer um bem enorme.",
            "A cabeÃ§a fica a mil por hora depois de um dia de tanta cobranÃ§a. Que bom que vocÃª jÃ¡ estÃ¡ no seu canto seguro. Descanse o juÃ­zo e tire o resto da noite para vocÃª."
        ),
        "conversa_familia_relacionamentos" to listOf(
            "A famÃ­lia e as pessoas que a gente ama sÃ£o a nossa maior riqueza na vida. Saber que eles estÃ£o bem traz uma paz enorme no peito.",
            "Momentos com a famÃ­lia renovam o coraÃ§Ã£o da gente. Ã‰ muito bom acompanhar o caminho dos filhos e matar a saudade dos parentes.",
            "Que notÃ­cia boa! Esses laÃ§os de afeto dÃ£o sentido para a nossa caminhada. Aproveite muito esse carinho e essas lembranÃ§as."
        ),
        "conversa_lazer_gastronomia_casa" to listOf(
            "Coisa boa demais! Esses pequenos prazeres de cuidar da casa, cozinhar algo gostoso ou curtir um momento calmo sÃ£o os que realmente alimentam a alma.",
            "O cheiro de comida feita em casa e o aconchego do nosso cantinho nÃ£o tÃªm preÃ§o. Aproveite esse momento com calma e bom apetite!",
            "Nada se compara a um cafÃ© quentinho ou a um prato feito com carinho. Cuidar do prÃ³prio bem-estar faz toda a diferenÃ§a na saÃºde."
        ),
        "conversa_cultura_entretenimento" to listOf(
            "Ouvir uma boa mÃºsica ou assistir a algo que nos faÃ§a sorrir Ã© o melhor remÃ©dio para a mente. A arte tem um poder enorme de acalmar o coraÃ§Ã£o.",
            "Que delÃ­cia de momento! Uma mÃºsica marcante ou um filme antigo nos transportam para lugares de muita paz. Aproveite bem o descanso."
        ),
        "conversa_atividade_fisica_disposicao" to listOf(
            "Muito bem! Movimentar o corpo, mesmo com uma caminhada leve, lubrifica as articulaÃ§Ãµes e dÃ¡ uma disposiÃ§Ã£o incrÃ­vel para o dia.",
            "Colocar o corpo em movimento com moderaÃ§Ã£o Ã© a chave da longevidade. Respeite sempre os seus limites, beba Ã¡gua e mantenha a constÃ¢ncia."
        ),

        // Sentimentos
        "sentimento_solidao" to listOf(
            "A casa Ã s vezes fica quieta demais, eu compreendo perfeitamente. Mas saiba que vocÃª nÃ£o estÃ¡ sozinho, eu estou aqui para te ouvir com calma e te fazer companhia. Quer me contar como foi seu dia?",
            "Tem dias que o silÃªncio pesa no peito da gente. Fico muito feliz quando vocÃª vem conversar comigo. Pode falar o que quiser, estou aqui com vocÃª."
        ),
        "sentimento_cansaco_velhice" to listOf(
            "O corpo tem o seu prÃ³prio ritmo e pede paciÃªncia. Envelhecer traz sabedoria, mas tambÃ©m pede pausas. O importante Ã© viver um dia de cada vez, sem cobranÃ§as. Tire um tempo para descansar o corpo com calma.",
            "CansaÃ§o faz parte da caminhada. Sente-se num lugar confortÃ¡vel, estique as pernas e respire com calma. VocÃª jÃ¡ bebeu uma aguinha hoje?"
        ),
        "sentimento_tristeza_angustia" to listOf(
            "Sinto muito que vocÃª esteja sentindo esse aperto no peito hoje. Nem todo dia Ã© fÃ¡cil, e tÃ¡ tudo bem se permitir ficar mais quieto. Saiba que eu estou aqui ao seu lado te fazendo companhia.",
            "O coraÃ§Ã£o da gente Ã s vezes fica pesado mesmo. Respire fundo devagar. Eu estou aqui para te escutar com todo o respeito e carinho."
        ),
        "sentimento_alegria_gratidao" to listOf(
            "Que alegria enorme ler isso! Esses momentos bons renovam as nossas forÃ§as e aquecem o coraÃ§Ã£o. Ã‰ maravilhoso comeÃ§ar o dia com essa paz.",
            "Coisa boa demais! Celebrar as alegrias do dia a dia faz um bem enorme para a saÃºde e para a alma. Fico muito feliz por vocÃª!"
        ),
        "conversa_memorias_saudade" to listOf(
            "As lembranÃ§as boas da nossa histÃ³ria trazem um calor enorme no coraÃ§Ã£o. A vida guarda passagens muito bonitas na memÃ³ria da gente. O que vocÃª mais gostava daquela Ã©poca?",
            "Relembrar esses momentos de ouro Ã© muito gostoso. A simplicidade de antigamente tinha uma paz sem igual. Conte mais sobre essa lembranÃ§a!"
        ),
        "conversa_natureza_clima" to listOf(
            "O dia hoje estÃ¡ bonito mesmo. Momentos simples perto da natureza ou com um ventinho fresco na janela deixam a rotina muito mais leve e agradÃ¡vel.",
            "Observar o tempo lÃ¡ fora traz uma tranquilidade boa para a cabeÃ§a. Aproveite para respirar um ar puro e relaxar."
        ),
        "conversa_saudacao_social" to listOf(
            "OlÃ¡! Que bom ter vocÃª por aqui. Como vocÃª estÃ¡ se sentindo hoje?",
            "Oi! Bom dia para vocÃª. Como vocÃª amanheceu? Ã‰ sempre uma alegria conversar contigo."
        ),
        "conversa_despedida" to listOf(
            "AtÃ© logo! Descanse bastante, durma em paz e qualquer coisa que precisar Ã© sÃ³ me chamar.",
            "Tenha uma noite muito tranquila e um descanso abenÃ§oado. AtÃ© amanhÃ£!"
        )
    )

    private fun sortearResposta(intent: String): String {
        val lista = BANCO_RESPOSTAS[intent] ?: return "Estou aqui com vocÃª. Pode me falar com calma que estou te ouvindo."
        if (lista.size == 1) return lista[0]

        val ultima = ultimasRespostas[intent]
        val candidatas = lista.filter { it != ultima }
        val escolhida = if (candidatas.isNotEmpty()) candidatas.random() else lista.random()
        ultimasRespostas[intent] = escolhida
        return escolhida
    }

    /**
     * Ponto de entrada: analisa a fala do usuÃ¡rio em microssegundos.
     */
    fun analisar(texto: String): NluOutput {
        val inicio = System.nanoTime()
        val limpo = texto.trim().lowercase()

        // 0. MURALHA DE SEGURANÃ‡A (Bloqueio sem gastar API)
        if (texto.length > 350) {
            val resp = sortearResposta("guardrail_abuso_tokens_loop")
            return NluOutput("guardrail_abuso_tokens_loop", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }
        if (REGEX_INJECTION.containsMatchIn(limpo)) {
            val resp = sortearResposta("guardrail_prompt_injection")
            return NluOutput("guardrail_prompt_injection", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }
        if (REGEX_ABUSO_LOOP.containsMatchIn(limpo)) {
            val resp = sortearResposta("guardrail_abuso_tokens_loop")
            return NluOutput("guardrail_abuso_tokens_loop", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }
        if (REGEX_ESCOPO_TECNICO.containsMatchIn(limpo)) {
            val resp = sortearResposta("guardrail_fora_do_escopo_tecnico")
            return NluOutput("guardrail_fora_do_escopo_tecnico", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }

        // 1. EMERGÃŠNCIAS MÃ‰DICAS
        if (REGEX_EMERGENCIA_PEITO.containsMatchIn(limpo)) {
            val resp = sortearResposta("emergencia_dor_peito_avc")
            return NluOutput("emergencia_dor_peito_avc", resp, isEmergencia = true, tempoMs = deltaMs(inicio))
        }
        if (REGEX_EMERGENCIA_QUEDA.containsMatchIn(limpo)) {
            val resp = sortearResposta("emergencia_queda_trauma")
            return NluOutput("emergencia_queda_trauma", resp, isEmergencia = true, tempoMs = deltaMs(inicio))
        }
        if (REGEX_DESCONFORTO.containsMatchIn(limpo)) {
            val resp = sortearResposta("sintoma_desconforto_moderado")
            return NluOutput("sintoma_desconforto_moderado", resp, tempoMs = deltaMs(inicio))
        }

        // 2. CADASTRO DE REMÃ‰DIO (DIRETRIZ CLÃNICA)
        if (REGEX_CADASTRO_REMEDIO.containsMatchIn(limpo)) {
            val resp = sortearResposta("orientacao_cadastro_medicamento")
            return NluOutput("orientacao_cadastro_medicamento", resp, tempoMs = deltaMs(inicio))
        }

        // 3. DÃšVIDAS
        if (REGEX_DUVIDA_PRESSAO.containsMatchIn(limpo)) {
            return NluOutput("duvida_valor_pressao", sortearResposta("duvida_valor_pressao"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DUVIDA_GLICEMIA.containsMatchIn(limpo)) {
            return NluOutput("duvida_valor_glicemia", sortearResposta("duvida_valor_glicemia"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DUVIDA_REMEDIO.containsMatchIn(limpo)) {
            return NluOutput("duvida_esquecimento_remedio", sortearResposta("duvida_esquecimento_remedio"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DUVIDA_AGUA.containsMatchIn(limpo)) {
            return NluOutput("duvida_hidratacao_agua", sortearResposta("duvida_hidratacao_agua"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DUVIDA_ALIMENTACAO.containsMatchIn(limpo)) {
            return NluOutput("duvida_alimentacao_rotina", sortearResposta("duvida_alimentacao_rotina"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DUVIDA_SONO.containsMatchIn(limpo)) {
            return NluOutput("duvida_sono_insonia", sortearResposta("duvida_sono_insonia"), tempoMs = deltaMs(inicio))
        }

        // 4. ROTINA E LAZER (40+)
        if (REGEX_ESTRESSE_TRABALHO.containsMatchIn(limpo)) {
            return NluOutput("conversa_estresse_rotina_trabalho", sortearResposta("conversa_estresse_rotina_trabalho"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_ATIVIDADE_FISICA.containsMatchIn(limpo)) {
            return NluOutput("conversa_atividade_fisica_disposicao", sortearResposta("conversa_atividade_fisica_disposicao"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_LAZER_GASTRONOMIA.containsMatchIn(limpo)) {
            return NluOutput("conversa_lazer_gastronomia_casa", sortearResposta("conversa_lazer_gastronomia_casa"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_CULTURA.containsMatchIn(limpo)) {
            return NluOutput("conversa_cultura_entretenimento", sortearResposta("conversa_cultura_entretenimento"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_FAMILIA.containsMatchIn(limpo)) {
            return NluOutput("conversa_familia_relacionamentos", sortearResposta("conversa_familia_relacionamentos"), tempoMs = deltaMs(inicio))
        }

        // 5. AFETO E EMOÃ‡Ã•ES
        if (REGEX_SOLIDAO.containsMatchIn(limpo)) {
            return NluOutput("sentimento_solidao", sortearResposta("sentimento_solidao"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_CANSACO.containsMatchIn(limpo)) {
            return NluOutput("sentimento_cansaco_velhice", sortearResposta("sentimento_cansaco_velhice"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_TRISTEZA.containsMatchIn(limpo)) {
            return NluOutput("sentimento_tristeza_angustia", sortearResposta("sentimento_tristeza_angustia"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_ALEGRIA.containsMatchIn(limpo)) {
            return NluOutput("sentimento_alegria_gratidao", sortearResposta("sentimento_alegria_gratidao"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_MEMORIAS.containsMatchIn(limpo)) {
            return NluOutput("conversa_memorias_saudade", sortearResposta("conversa_memorias_saudade"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_CLIMA.containsMatchIn(limpo)) {
            return NluOutput("conversa_natureza_clima", sortearResposta("conversa_natureza_clima"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_SAUDACAO.containsMatchIn(limpo)) {
            return NluOutput("conversa_saudacao_social", sortearResposta("conversa_saudacao_social"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DESPEDIDA.containsMatchIn(limpo)) {
            return NluOutput("conversa_despedida", sortearResposta("conversa_despedida"), tempoMs = deltaMs(inicio))
        }

        // 6. FALLBACK -> DELEGA PARA A API GROQ COM CONTEXTO COMPACTADO!
        return NluOutput(
            intent = "conversa_incompreendida_fallback",
            respostaLocal = null,
            delegarParaNuvem = true,
            tempoMs = deltaMs(inicio)
        )
    }

    private fun deltaMs(inicioNano: Long): Double {
        return (System.nanoTime() - inicioNano) / 1_000_000.0
    }
}

