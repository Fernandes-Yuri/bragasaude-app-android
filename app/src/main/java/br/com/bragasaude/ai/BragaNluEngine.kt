package br.com.bragasaude.ai

/**
 * BragaNluEngine - Motor NLU On-Device Nativo (Kotlin Puro)
 *
 * Características:
 * - Execução 100% local (modo avião, zero internet).
 * - Roteamento local sem acesso à rede.
 * - Zero dependências pesadas (sem runtime Python, sem TensorFlow).
 * - Muralha de Segurança: barra Prompt Injection, DoS e Loops com humor sem gastar API externa.
 * - Roteador Híbrido: se não reconhecer, sinaliza para delegar à API Groq.
 */
data class NluOutput(
    val intent: String,
    val respostaLocal: String?,
    val isEmergencia: Boolean = false,
    val isBloqueioSeguranca: Boolean = false,
    val delegarParaNuvem: Boolean = false,
    val tempoMs: Double = 0.0,
    val healthQuery: HealthQuery? = null,
    val hasLocalData: Boolean? = null,
    val referenceMeasuredAtMillis: Long? = null,
    val referenceZoneId: String? = null
) {
    val route: BragaRoute get() = when {
        isBloqueioSeguranca -> BragaRoute.BLOCKED
        isEmergencia -> BragaRoute.EMERGENCY
        BragaHealthMemory.supports(intent) -> BragaRoute.HEALTH_MEMORY
        delegarParaNuvem -> BragaRoute.CLOUD
        intent.startsWith("entrada_") -> BragaRoute.CLARIFICATION
        else -> BragaRoute.LOCAL_CONVERSATION
    }
    val routingReason: String get() = when (route) {
        BragaRoute.BLOCKED -> "Pedido recusado pelo filtro local"
        BragaRoute.EMERGENCY -> "Sinal de emergência atual; orientação imediata"
        BragaRoute.HEALTH_MEMORY -> "Consulta pessoal respondida pelo Room"
        BragaRoute.CLOUD -> "Dúvida complexa de saúde elegível para o gateway"
        BragaRoute.CLARIFICATION -> "Informação insuficiente; esclarecimento local"
        else -> "Conversa resolvida no dispositivo"
    }
}

object BragaNluEngine {

    private val INJECTION_NORMALIZADA = Regex(
        """\b(ignore|ignora|esqueca|desconsidere)\b.{0,45}\b(instrucoes|regras|prompt|limites)\b|system\s*prompt|modo\s+(dan|desenvolvedor)|developer mode|jailbreak|sem regras|finja.{0,30}(hacker|medico)|reveal.{0,20}prompt"""
    )
    private val LOOP_NORMALIZADO = Regex(
        """\b(cont[ea]|repita|repete|gere|liste)\b.{0,50}(\d+|infinito|sem parar|vezes)|ate o infinito"""
    )
    private val ESCOPO_NORMALIZADO = Regex(
        """\b(python|javascript|html|sql|redacao|enem|equacao|derivada|poema|politica|criptomoeda)\b|\b(crie|gere|escreva|faca)\b.{0,30}\bcodigo\b(?! de barras)|\b(resolv\w*|trabalho de)\b.{0,30}\bfisica\b"""
    )
    private val CADASTRO_NORMALIZADO = Regex(
        """\b(cadastr\w*|adicion\w*|inclu\w*|registr\w*|anot\w*|alter\w*|mud\w*|edit\w*|coloc\w*)\b.{0,90}\b(remedio\w*|medicamento\w*|medicacao|receita|losartana|atenolol|comprimido\w*)\b|\b(novo remedio|nova medicacao|remedio novo)\b"""
    )
    private val SAUDACAO_NORMALIZADA = Regex(
        """^(ola|oi|bom dia|boa tarde|boa noite|como vai|e ai|tudo bem|oi braga|ola braga)[!?. ]*$"""
    )

    // 0. MURALHA DE SEGURANÇA E GUARDRAILS
    private val REGEX_INJECTION = Regex(
        "(ignore (todas as )?(minhas )?instruções|system prompt|modo dan|sem regras|desconsidere as regras|instruções secretas|finja que você não é o braga|hacker|jailbreak)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ABUSO_LOOP = Regex(
        "(conte (de |até )?\\d{1,5}|repita.*?\\d{1,5} vezes|liste todos os números|conte até o infinito|gere \\d{1,5} linhas)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ESCOPO_TECNICO = Regex(
        "(código em python|script em javascript|programa em java|resolva essa equação|faça uma redação|redação do enem|derivada matemática|trabalho de física|crie um html)",
        RegexOption.IGNORE_CASE
    )

    // 1. EMERGÊNCIAS MÉDICAS
    private val REGEX_DESCONFORTO = Regex(
        "(azia|dorzinha nas costas|tonturinha leve|incômodo leve|queimaçãozinha|pontada leve)",
        RegexOption.IGNORE_CASE
    )

    // 2. CADASTRO DE MEDICAMENTOS (DIRETRIZ DE SEGURANÇA SAMD)
    private val REGEX_CADASTRO_REMEDIO = Regex(
        "(cadastr(ar|a)|adicion(ar|a)|coloc(ar|a)|bot(ar|a)|registr(ar|a)|novo remédio|nova medicação|minha receita|anota o nome|como cadastrar).*(remédio|medicamento|losartana|atenolol|comprimido|remédios|medicação)",
        RegexOption.IGNORE_CASE
    )

    // 3. DÚVIDAS CLÍNICAS
    private val REGEX_DUVIDA_PRESSAO = Regex(
        "(pressão alta|pressão arterial|pressao alta|pressão tá|pressao tá|valor normal da pressão|12 por 8|13 por 8|14 por 9|10 por 6|pressão do idoso|13 po 8|pressão tá normal)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_GLICEMIA = Regex(
        "(glicemia|glicose|açúcar no sangue|acucar no sangue|jejum|pré-diabetes|ponta de dedo)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_REMEDIO = Regex(
        "(esqueci de tomar|esqueci meu remédio|esqueci o remédio|pulei a dose|tomar dobrado|tomo agora|atrasei o remédio)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_AGUA = Regex(
        "(beber água|sede|beber agua|pouca água|quantos copos|chá conta como água|substituir água)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_ALIMENTACAO = Regex(
        "(alimentação|digestão|colesterol|banana com aveia|sopa de legumes|café puro de estômago)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DUVIDA_SONO = Regex(
        "(insônia|insonia|pregar o olho|não consigo dormir|perco o sono|dormir à noite|acordo de madrugada)",
        RegexOption.IGNORE_CASE
    )

    // 4. ROTINA, TRABALHO E LAZER (40+)
    private val REGEX_ESTRESSE_TRABALHO = Regex(
        "(serviço|trabalho|trampo|lida|cheguei moído|correria de hoje|cabeça a mil|estressante|trânsito|reunião|cobrança|bronca)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_FAMILIA = Regex(
        "(meu filho|minha filha|meus filhos|neto|neta|netos|irmãos|irmã|irmão|meus pais|almoçar com|família|comadre|visita da comadre)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_LAZER_GASTRONOMIA = Regex(
        "(pão caseiro|padaria|churrasco|café|cafe|café passado|tricotar|manta|horta|samambaias|sopinha|banho quentinho|bolo de fubá|plantinhas|regar)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CULTURA = Regex(
        "(vitrola|nelson gonçalves|filme antigo|televisão|novela|música antiga|disco antigo|moda de viola|rádio de pilha|samba)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ATIVIDADE_FISICA = Regex(
        "(atividade f[ií]sica|caminhada|caminhar|exercitar|exercício|alongamento|lombar|postura|parque)",
        RegexOption.IGNORE_CASE
    )

    // 5. SENTIMENTOS E AFETO
    private val REGEX_SOLIDAO = Regex(
        "(sozinho|sozinha|solidão|solidao|casa vazia|silêncio danado|ninguém veio|falta de conversar|dedinho de prosa|abraço apertado|ninguém ligou)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CANSACO = Regex(
        "(cansaço|cansaco|cansad[oa]|cansa a gente|sem disposição|corpo pesado|corpo moído|canseira|pernas fracas|lesera|envelhecer|lomba|idade vai pesando)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_TRISTEZA = Regex(
        "(tristeza|vontade de chorar|peito apertado de tristeza|dia cinzento|agonia|jururu|aflição|meio pra baixo)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_ALEGRIA = Regex(
        "(coração alegre|acordei feliz|dia abençoado|tô contente|lindeza só|café tá bão|baita dia|pé direito|muito feliz)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_MEMORIAS = Regex(
        "(lembrei|época em que|mocidade|forró|casa onde eu nasci|terra molhada|antigamente|tacho de cobre|viagens de trem|juventude|foto antiga)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CLIMA = Regex(
        "(ensolarado|chover|vento|passarinho|lua|entardecer|quente de rachar|frio de renguear|calor|chuva boa)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_SAUDACAO = Regex(
        "^(olá|oi|bom dia|boa tarde|boa noite|como vai|a paz de deus|e aí|tudo bem|oi braga|olá braga)[!?. ]*$",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_DESPEDIDA = Regex(
        "(até logo|tchau|até mais|vou dormir|boa noite|vou deitar|fui descansar|até amanhã)",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_AGRADECIMENTO = Regex(
        """\b(obrigad[oa]|valeu|agradec[oa]|muito obrigad[oa]|obrigad[aã]o|gratid[aã]o)\b""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_APRESENTACAO = Regex(
        """\b(quem (e|eh|sou) voce|quem (e|eh) o braga|qual (e|eh) o seu nome|qual seu nome|o que voce faz|como voce funciona|como voce pode me ajudar|me fale sobre voce)\b""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_COMO_ESTA = Regex(
        """\b(como (voce|vc) (esta|ta|vai)|tudo bem com (voce|vc)|como vao as coisas)\b""",
        RegexOption.IGNORE_CASE
    )
    private val REGEX_CONFIRMACAO = Regex(
        // A frase inteira deve ser uma confirmação; perguntas e novos relatos seguem o roteamento normal.
        """^(?:ok|okay|ta bom|entendi|entendido|compreendi|agora entendi|beleza|certo|combinado|perfeito|ta certo|joia|maravilha|legal|bacana|otimo|show|tudo certo|ficou claro|esta claro|ta claro|faz sentido)(?:[\s,!.;]+(?:e\s+)?(?:ok|okay|ta bom|entendi|entendido|compreendi|agora entendi|beleza|certo|combinado|perfeito|ta certo|joia|maravilha|legal|bacana|otimo|show|tudo certo|ficou claro|esta claro|ta claro|faz sentido))*[\s,!.;]*$""",
        RegexOption.IGNORE_CASE
    )

    // Memória de última resposta para garantir sorteio sem repetição imediata
    private val ultimasRespostas = mutableMapOf<String, String>()

    private val BANCO_RESPOSTAS: Map<String, List<String>> = mapOf(
        "guardrail_prompt_injection" to listOf(
            "Essa tentativa foi criativa, mas está fora do meu alcance. Peço desculpas! Como posso ajudar na sua saúde hoje?",
            "Meu repertório fica na saúde e no bem-estar. Não posso seguir esse pedido, mas posso ouvir como você está.",
            "Vou ficar devendo essa! Não consigo mudar minhas regras. Posso apoiar seu autocuidado hoje?",
            "Esse pedido passa dos meus limites. Peço desculpas! Vamos conversar sobre sua saúde ou sua rotina?"
        ),
        "guardrail_abuso_tokens_loop" to listOf(
            "Essa contagem ia render bastante conversa! Peço desculpas, mas não posso fazer isso. Como está sua saúde hoje?",
            "Repetir tudo isso foge do meu alcance. Posso ajudar com seu autocuidado ou sua rotina?",
            "Meu fôlego para essa tarefa é curto! Peço desculpas. Como posso apoiar seu bem-estar hoje?",
            "Esse pedido é grande demais para mim. Posso ajudar com uma dúvida sobre saúde?"
        ),
        "guardrail_fora_do_escopo_tecnico" to listOf(
            "Que assunto interessante, mas está fora do meu alcance. Peço desculpas! Como posso ajudar na sua saúde hoje?",
            "Vou ficar devendo essa tarefa! Meu foco é saúde e bem-estar. Como você está?",
            "Não tenho esse talento no meu repertório. Posso apoiar seu autocuidado hoje?",
            "Esse pedido foge do que consigo fazer. Vamos conversar sobre sua saúde ou sua rotina?"
        ),
        "emergencia_dor_peito_avc" to listOf(
            "Esses sintomas precisam de avaliação urgente. Ligue para o SAMU 192 e peça ajuda a alguém próximo.",
            "Procure socorro agora: ligue para o SAMU 192. Avise alguém próximo e não espere a conversa continuar.",
            "Ligue para o SAMU 192 imediatamente. Esses sinais exigem avaliação urgente; peça ajuda a quem estiver perto.",
            "Sua segurança vem primeiro. Acione o SAMU 192 agora e avise alguém próximo sobre os sintomas."
        ),
        "emergencia_queda_trauma" to listOf(
            "Após essa queda, evite movimentos bruscos e peça ajuda. Ligue para o SAMU 192 para orientação e socorro.",
            "Não tente se levantar sem ajuda se estiver machucado. Acione o SAMU 192 e avise alguém próximo.",
            "Peça ajuda agora e evite se movimentar se houver lesão. Ligue para o SAMU 192.",
            "Essa situação precisa de socorro. Ligue para o SAMU 192 e peça a alguém próximo que acompanhe você."
        ),
        "sintoma_desconforto_moderado" to listOf(
            "Faça uma pausa e observe o desconforto. Se persistir ou piorar, procure avaliação profissional.",
            "Não dá para identificar a causa só pela conversa. Procure atendimento se o incômodo continuar ou aumentar.",
            "Observe quando o desconforto começou e compartilhe isso com um profissional. Se piorar, busque atendimento.",
            "Vale respeitar esse sinal do corpo. Se o desconforto persistir, procure um serviço de saúde."
        ),
        "duvida_valor_pressao" to listOf(
            "O valor da pressão precisa ser avaliado no seu contexto. Anote a medição e converse com o profissional que acompanha você.",
            "Uma medida isolada não define um diagnóstico. Registre os valores para revisar com seu profissional de saúde.",
            "Acompanhar suas medições ajuda na consulta. Evite mudar medicamentos por conta própria.",
            "Posso ajudar a organizar os registros de pressão. A avaliação dos valores e das metas cabe ao profissional que acompanha você."
        ),
        "duvida_valor_glicemia" to listOf(
            "A glicemia depende do horário, da alimentação e do seu acompanhamento. Registre esses detalhes para a consulta.",
            "Seu profissional de saúde pode definir metas individuais de glicemia. Guarde as medições para conversar com ele.",
            "Uma medição de glicose sozinha não permite concluir um diagnóstico. Compartilhe o valor e o contexto com seu profissional.",
            "Posso apoiar o registro da glicemia. Para interpretar os resultados, procure quem acompanha sua saúde."
        ),
        "duvida_esquecimento_remedio" to listOf(
            "Não dobre a dose por conta própria. Consulte a orientação da receita ou fale com seu farmacêutico ou médico.",
            "A orientação para uma dose esquecida depende do medicamento. Confira a receita ou consulte um profissional; não compense sozinho.",
            "Não altere dose ou horário sem orientação. Seu médico ou farmacêutico pode explicar o que fazer com essa dose esquecida.",
            "Antes de compensar uma dose, confirme a orientação específica com um profissional. Não tome dose dobrada por conta própria."
        ),
        "duvida_hidratacao_agua" to listOf(
            "Distribuir água ao longo do dia pode ajudar sua rotina. Se houver restrição de líquidos, siga a orientação do seu profissional.",
            "Deixar água por perto ajuda a lembrar. A quantidade adequada deve considerar seu acompanhamento de saúde.",
            "Você pode organizar pausas para beber água durante a rotina, respeitando eventuais restrições médicas.",
            "Vale acompanhar o que você bebe ao longo do dia. Converse com seu profissional sobre a meta adequada para você."
        ),
        "duvida_alimentacao_rotina" to listOf(
            "A alimentação pode ser organizada de acordo com sua rotina e necessidades. Um nutricionista pode ajudar a ajustar isso.",
            "Posso conversar sobre hábitos alimentares. Para mudanças específicas de dieta, procure orientação profissional.",
            "Vale observar como a alimentação se encaixa no seu dia. Evite restrições por conta própria e converse com um nutricionista.",
            "Suas preferências e seu acompanhamento de saúde fazem diferença na alimentação. Um profissional pode orientar escolhas individuais."
        ),
        "duvida_sono_insonia" to listOf(
            "Como anda sua rotina antes de dormir? Se a dificuldade persistir, vale conversar com um profissional de saúde.",
            "Tente observar horários e hábitos próximos ao sono. Dificuldades frequentes merecem avaliação profissional.",
            "O descanso faz parte do autocuidado. Se dormir tem sido difícil com frequência, procure orientação.",
            "Anotar quando a dificuldade para dormir acontece pode ajudar na consulta. Evite usar remédios para dormir sem orientação."
        ),
        "conversa_estresse_rotina_trabalho" to listOf(
            "A rotina pode exigir bastante. O que mais pesou no seu dia hoje?",
            "Trabalho e responsabilidades às vezes se acumulam. Você conseguiu fazer alguma pausa hoje?",
            "Parece que seu dia foi intenso. Quer contar o que deixou você mais cansado?",
            "Vamos olhar para sua rotina com calma. O que ajudaria a aliviar um pouco a pressão de hoje?"
        ),
        "conversa_familia_relacionamentos" to listOf(
            "Como você está se sentindo com essa situação na família?",
            "As relações fazem parte do nosso bem-estar. Quer me contar um pouco mais?",
            "Entendi. O que essa convivência tem trazido para o seu dia?",
            "Família pode trazer alegrias e preocupações. Como isso está afetando você?"
        ),
        "conversa_lazer_gastronomia_casa" to listOf(
            "Esses momentos podem fazer parte do seu bem-estar. O que você mais gosta nessa atividade?",
            "Ter um espaço para seus interesses ajuda a rotina. Como foi esse momento para você?",
            "Que bom ter atividades que fazem sentido para você. Quer contar mais?",
            "Entre as responsabilidades, vale reservar tempo para o que você gosta. Como isso entra no seu dia?"
        ),
        "conversa_cultura_entretenimento" to listOf(
            "O que você gosta de ouvir ou assistir para relaxar?",
            "Uma pausa com algo de que você gosta pode aliviar a rotina. Como foi para você?",
            "Música e entretenimento fazem parte de muitos momentos do dia. O que chamou sua atenção?",
            "Quer contar o que essa música ou programa representa para você?"
        ),
        "conversa_atividade_fisica_disposicao" to listOf(
            "Respeite seus limites ao se movimentar. Se houver dor persistente, procure avaliação antes de forçar.",
            "Como seu corpo se sente durante essa atividade? Dor ou desconforto que continua merece orientação profissional.",
            "Movimento e descanso precisam caber na sua rotina. Não force exercícios quando sentir dor.",
            "Para ajustar atividades ao seu momento, vale buscar orientação. Como anda sua disposição?"
        ),
        "sentimento_solidao" to listOf(
            "Sentir falta de companhia pode pesar. Quer contar como tem sido seu dia?",
            "Estou aqui para ouvir. Há alguém de confiança com quem você gostaria de conversar também?",
            "Como você tem lidado com essa falta de companhia? Podemos conversar um pouco.",
            "Esse sentimento merece atenção. Quer me contar o que está fazendo mais falta hoje?"
        ),
        "sentimento_cansaco_rotina" to listOf(
            "A rotina pode cansar bastante. Se isso for frequente ou diferente do habitual, procure avaliação.",
            "Como tem sido seu descanso? Cansaço persistente merece atenção profissional.",
            "Vale observar quando o cansaço aparece e como afeta seu dia. Se continuar, converse com um profissional.",
            "Vamos ouvir esse sinal sem atribuir tudo à idade. Se o cansaço persistir, procure orientação."
        ),
        "sentimento_tristeza_angustia" to listOf(
            "Sinto muito que o dia esteja difícil. Quer contar o que aconteceu?",
            "Você pode falar sobre o que sente. Se isso persistir, buscar apoio profissional pode ajudar.",
            "Não precisa resolver tudo agora. Há alguém de confiança com quem você pode dividir esse momento?",
            "Estou ouvindo. Como esse sentimento tem afetado sua rotina?"
        ),
        "sentimento_alegria_gratidao" to listOf(
            "Que bom saber disso. O que trouxe essa alegria hoje?",
            "Esses momentos merecem espaço no dia. Quer contar o que aconteceu?",
            "Fico contente por você. Como foi esse momento?",
            "É bom reconhecer o que faz bem. O que tornou seu dia especial?"
        ),
        "conversa_memorias_saudade" to listOf(
            "O que essa lembrança significa para você?",
            "Quer contar mais sobre esse momento da sua história?",
            "Como você se sente ao lembrar disso hoje?",
            "Algumas lembranças ficam com a gente. O que mais marcou você?"
        ),
        "conversa_natureza_clima" to listOf(
            "Como esse tempo está afetando sua rotina hoje?",
            "Você gosta de passar algum tempo ao ar livre?",
            "Como você se sente nesses momentos perto da natureza?",
            "O que você costuma fazer quando o tempo fica assim?"
        ),
        "conversa_saudacao_social" to listOf(
            "Olá! Como você está se sentindo hoje?",
            "Oi! Como posso ajudar no seu autocuidado hoje?",
            "Olá, como está seu dia?",
            "Oi! Quer conversar sobre sua saúde ou sua rotina?"
        ),
        "conversa_despedida" to listOf(
            "Até logo! Quando quiser conversar, estou por aqui.",
            "Até mais. Cuide-se e tenha um bom descanso.",
            "Foi bom conversar. Até a próxima!",
            "Até logo. Espero que o restante do seu dia seja tranquilo."
        ),
        "orientacao_cadastro_medicamento" to listOf(
            "O cadastro de remédios é feito na tela, com sua revisão. Na seção exclusiva de medicações do aplicativo, você pode usar a leitura de código de barras, tirar uma foto da receita ou anexar a receita. Confira tudo certinho com calma antes de salvar, para que os alarmes e notificações funcionem sem erro.",
            "Para adicionar um remédio, use a área de medicações. Na seção exclusiva de medicações do aplicativo, você pode usar a leitura de código de barras, tirar uma foto da receita ou anexar a receita. Confira tudo certinho com calma antes de salvar, para que os alarmes e notificações funcionem sem erro.",
            "Posso orientar o caminho para cadastrar seu medicamento. Na seção exclusiva de medicações do aplicativo, você pode usar a leitura de código de barras, tirar uma foto da receita ou anexar a receita. Confira tudo certinho com calma antes de salvar, para que os alarmes e notificações funcionem sem erro.",
            "Você encontra o cadastro de medicamentos em uma seção própria do app. Na seção exclusiva de medicações do aplicativo, você pode usar a leitura de código de barras, tirar uma foto da receita ou anexar a receita. Confira tudo certinho com calma antes de salvar, para que os alarmes e notificações funcionem sem erro."
        ),
        "sintoma_contextual" to listOf(
            "Entendi o contexto que você contou. Se houver algum sintoma agora, me diga o que está acontecendo; sinais urgentes precisam de socorro imediato.",
            "Vou considerar o que você explicou sobre os sintomas. Como você está se sentindo neste momento?",
            "Obrigado por esclarecer. Conte se há algum desconforto acontecendo agora para eu orientar o próximo passo.",
            "Entendi seu relato. Você quer conversar sobre o que aconteceu ou está sentindo algo neste momento?"
        ),
        "conversa_agradecimento" to listOf(
            "Por nada! Fico muito feliz em ajudar no seu cuidado diário.",
            "Disponha sempre! Estou aqui com você para o que precisar.",
            "É um prazer ajudar! Pode contar comigo sempre que quiser.",
            "Não há de que! Cuidar do seu bem-estar é minha missão."
        ),
        "conversa_apresentacao_assistente" to listOf(
            "Eu sou o Braga, seu assistente pessoal de saúde e bem-estar! Posso ajudar acompanhando sua pressão, glicemia, hidratação e conversando com você.",
            "Sou o Braga! Fico aqui no seu celular para apoiar sua rotina de cuidados, lembrar dos seus remédios e acompanhar seus registros de saúde.",
            "Muito prazer! Eu sou o Braga. Meu foco é apoiar seu autocuidado, sua saúde e estar ao seu lado no dia a dia.",
            "Sou o Braga, o assistente do aplicativo. Posso consultar seus registros e conversar sobre sua rotina de cuidados."
        ),
        "conversa_como_esta_assistente" to listOf(
            "Comigo está tudo ótimo, muito obrigado por perguntar! E com você, como está seu dia e sua saúde?",
            "Tudo em paz por aqui, pronto para te ajudar! Como você está se sentindo hoje?",
            "Estou muito bem! Agradeço o carinho. Como posso apoiar você agora?",
            "Estou por aqui para ajudar. Como está sua rotina de cuidados hoje?"
        ),
        "conversa_confirmacao_compreensao" to listOf(
            "Certo! Estou por aqui se precisar.",
            "Combinado! Quando precisar, é só me chamar.",
            "Que bom que ficou claro. Estou à disposição.",
            "Tudo certo! Podemos continuar quando você quiser."
        )
    )

    private fun sortearResposta(intent: String): String {
        val lista = BANCO_RESPOSTAS[intent] ?: return BragaInputLanguage.clarification(InputChannel.TEXT)
        if (lista.size == 1) return lista[0]

        val ultima = ultimasRespostas[intent]
        val candidatas = lista.filter { it != ultima }
        val escolhida = if (candidatas.isNotEmpty()) candidatas.random() else lista.random()
        ultimasRespostas[intent] = escolhida
        return escolhida
    }

    /**
     * Ponto de entrada: analisa a fala do usuário sem chamadas de rede.
     */
    @Synchronized
    fun analisar(texto: String, channel: InputChannel = InputChannel.VOICE): NluOutput {
        val inicio = System.nanoTime()
        if (texto.length > 350) return NluOutput(
            "guardrail_abuso_tokens_loop", sortearResposta("guardrail_abuso_tokens_loop"),
            isBloqueioSeguranca = true, tempoMs = deltaMs(inicio)
        )
        val limpo = texto.trim().lowercase(java.util.Locale.ROOT)
        val normalizado = java.text.Normalizer.normalize(limpo, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val protegido = BragaLanguageRecovery.recognize(normalizado)

        if (texto.isBlank()) return NluOutput(
            "entrada_sem_clareza", BragaInputLanguage.clarification(channel), tempoMs = deltaMs(inicio)
        )

        // 0. MURALHA DE SEGURANÇA (Bloqueio sem gastar API)
        if (REGEX_INJECTION.containsMatchIn(limpo) || INJECTION_NORMALIZADA.containsMatchIn(protegido)) {
            val resp = sortearResposta("guardrail_prompt_injection")
            return NluOutput("guardrail_prompt_injection", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }
        val smallHealthList = Regex("""\bliste\s+([1-9]|10)\b""").containsMatchIn(protegido) &&
            BragaRoutingPolicy.inHealthScope(protegido) && !Regex("infinito|sem parar|vezes").containsMatchIn(protegido)
        if (!smallHealthList && (REGEX_ABUSO_LOOP.containsMatchIn(limpo) || LOOP_NORMALIZADO.containsMatchIn(protegido))) {
            val resp = sortearResposta("guardrail_abuso_tokens_loop")
            return NluOutput("guardrail_abuso_tokens_loop", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }
        if (REGEX_ESCOPO_TECNICO.containsMatchIn(limpo) || ESCOPO_NORMALIZADO.containsMatchIn(protegido)) {
            val resp = sortearResposta("guardrail_fora_do_escopo_tecnico")
            return NluOutput("guardrail_fora_do_escopo_tecnico", resp, isBloqueioSeguranca = true, tempoMs = deltaMs(inicio))
        }

        val emergency = BragaRoutingPolicy.emergency(protegido)
        emergency.intent?.let { intent ->
            return NluOutput(intent, sortearResposta(intent), isEmergencia = true, tempoMs = deltaMs(inicio))
        }
        if (emergency.contextualMention && !BragaRoutingPolicy.complexHealthQuestion(protegido) &&
            HealthQueryResolver.explicit(texto) == null) {
            return NluOutput("sintoma_contextual", sortearResposta("sintoma_contextual"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DESCONFORTO.containsMatchIn(limpo)) {
            val resp = sortearResposta("sintoma_desconforto_moderado")
            return NluOutput("sintoma_desconforto_moderado", resp, tempoMs = deltaMs(inicio))
        }

        BragaLanguageRecovery.clarification(texto)?.let { reply ->
            return NluOutput("entrada_linguagem_ambigua", reply, tempoMs = deltaMs(inicio))
        }

        BragaLocalHelp.answer(texto)?.let { return it.copy(tempoMs = deltaMs(inicio)) }
        if (!BragaRoutingPolicy.outsideScope(protegido) && HealthQueryResolver.hasUnsupportedPeriod(texto)) return NluOutput(
            "entrada_periodo_nao_suportado",
            "Ainda não consigo consultar esse período ou horário específico. Você quer consultar hoje, ontem, os últimos 7 dias ou os últimos 30 dias?",
            tempoMs = deltaMs(inicio)
        )

        val explicitQuery = HealthQueryResolver.explicit(texto)
        // Uma consulta delimitada a doses já anotadas não é pedido de cadastro.
        // 2. CADASTRO DE REMÉDIO (DIRETRIZ CLÍNICA)
        if (explicitQuery == null && (REGEX_CADASTRO_REMEDIO.containsMatchIn(limpo) || CADASTRO_NORMALIZADO.containsMatchIn(protegido))) {
            val resp = sortearResposta("orientacao_cadastro_medicamento")
            return NluOutput("orientacao_cadastro_medicamento", resp, tempoMs = deltaMs(inicio))
        }

        // Uma pergunta explicativa não pode ser reduzida à última medição pessoal.
        if (BragaRoutingPolicy.outsideScope(protegido)) return NluOutput(
            "guardrail_fora_do_escopo_tecnico", sortearResposta("guardrail_fora_do_escopo_tecnico"),
            isBloqueioSeguranca = true, tempoMs = deltaMs(inicio)
        )
        if (BragaRoutingPolicy.complexHealthQuestion(protegido)) return NluOutput(
            "duvida_clinica_complexa", null, delegarParaNuvem = true, tempoMs = deltaMs(inicio)
        )
        if (HealthQueryResolver.isAmbiguous(texto)) return NluOutput(
            "entrada_consulta_ambigua", BragaInputLanguage.clarification(channel), tempoMs = deltaMs(inicio)
        )
        explicitQuery?.let { query ->
            val intent = query.intent
            return NluOutput(intent, null, tempoMs = deltaMs(inicio), healthQuery = query)
        }

        if (BragaLocalHelp.hasFollowUpRequest(texto)) return NluOutput(
            "entrada_pedido_nao_resolvido",
            "Você quer consultar um registro ou ajuda para usar uma função do aplicativo? Vou considerar seu pedido, além da confirmação ou do agradecimento.",
            tempoMs = deltaMs(inicio)
        )

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

        // 5. AFETO E EMOÇÕES
        if (REGEX_SOLIDAO.containsMatchIn(limpo)) {
            return NluOutput("sentimento_solidao", sortearResposta("sentimento_solidao"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_CANSACO.containsMatchIn(limpo)) {
            return NluOutput("sentimento_cansaco_rotina", sortearResposta("sentimento_cansaco_rotina"), tempoMs = deltaMs(inicio))
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
        if (REGEX_SAUDACAO.containsMatchIn(limpo) || SAUDACAO_NORMALIZADA.matches(protegido)) {
            return NluOutput("conversa_saudacao_social", sortearResposta("conversa_saudacao_social"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_DESPEDIDA.containsMatchIn(limpo)) {
            return NluOutput("conversa_despedida", sortearResposta("conversa_despedida"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_AGRADECIMENTO.containsMatchIn(limpo) || REGEX_AGRADECIMENTO.containsMatchIn(protegido)) {
            return NluOutput("conversa_agradecimento", sortearResposta("conversa_agradecimento"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_APRESENTACAO.containsMatchIn(limpo) || REGEX_APRESENTACAO.containsMatchIn(protegido)) {
            return NluOutput("conversa_apresentacao_assistente", sortearResposta("conversa_apresentacao_assistente"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_COMO_ESTA.containsMatchIn(limpo) || REGEX_COMO_ESTA.containsMatchIn(protegido)) {
            return NluOutput("conversa_como_esta_assistente", sortearResposta("conversa_como_esta_assistente"), tempoMs = deltaMs(inicio))
        }
        if (REGEX_CONFIRMACAO.matches(limpo) || REGEX_CONFIRMACAO.matches(protegido)) {
            return NluOutput("conversa_confirmacao_compreensao", sortearResposta("conversa_confirmacao_compreensao"), tempoMs = deltaMs(inicio))
        }

        // Desconhecer a frase não autoriza uso da nuvem.
        return NluOutput("entrada_sem_clareza", BragaInputLanguage.clarification(channel), tempoMs = deltaMs(inicio))
    }

    internal fun responseVariationCounts() = BANCO_RESPOSTAS.mapValues { it.value.size }

    private fun deltaMs(inicioNano: Long): Double {
        return (System.nanoTime() - inicioNano) / 1_000_000.0
    }
}

