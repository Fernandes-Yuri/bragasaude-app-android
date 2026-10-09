package br.com.bragasaude.ai

/** Respostas de escopo fechado; não executa ações nem infere causas individuais. */
internal object BragaLocalHelp {
    private val variation = Regex("""^(?:por que|porque) (?:(?:ela |ele |isso )|(?:a |minha |a minha )?(?:pressao|glicemia|glicose) )?(?:subiu|baixou|caiu|aumentou|diminuiu|varia|variou|mudou|oscila|oscilou)(?: tanto)?$""")
    private val pressure = Regex("""\bpressao\b""")
    private val glucose = Regex("""\b(?:glicemia|glicose)\b""")
    private val exam = Regex("""\bexames?\b""")
    private val addExam = Regex("""\b(adiciono|adicionar|adiciona|adicione|anexo|anexar|anexa|anexe|envio|enviar|envia|envi[eo]|cadastro|cadastrar)\b""")
    private val correction = Regex("""\b(corrijo|corrigir|corrige|corrija|edito|editar|edita|edite)\b""")
    private val record = Regex("""\b(registro|registros|pressao|glicemia|glicose|agua|hidratacao)\b""")
    private val missing = Regex("""\b(nao aparece|nao apareceu|nao encontro|nao tem)\b""")
    private val request = Regex("""\b(como|onde|por que|porque|posso|pode|quero)\b""")
    private val repair = Regex("""^(?:(?:me )?(?:explica|explique)(?: melhor| de novo)|nao entendi|ainda nao entendi|nao ficou claro)$""")
    private val clinicalExplanation = Regex("""\b(o que (e|significa|causa)|afeta|interfere|tratamento|remedio|medicamento|pressao alta|pressao baixa)\b""")

    fun hasFollowUpRequest(text: String): Boolean {
        val normalized = BragaRoutingPolicy.normalize(text).trimEnd('.', '!', '?', ' ')
        val input = BragaDialogueRequest.main(text)
        return input != normalized && (text.contains('?') ||
            Regex("""\b(como|qual|quais|quanto|onde|quando|por que|porque|o que|posso|devo|quero|explica|explique|ajuda)\b""").containsMatchIn(input))
    }

    private fun operational(input: String): NluOutput? {
        if (!request.containsMatchIn(input)) return null
        if (exam.containsMatchIn(input) && addExam.containsMatchIn(input) && clinicalExplanation.containsMatchIn(input)) return NluOutput(
            "entrada_pedido_misto",
            "Você quer ajuda para anexar o exame ou uma explicação sobre o resultado? Vamos tratar um pedido de cada vez."
        )
        if (exam.containsMatchIn(input) && addExam.containsMatchIn(input)) return NluOutput(
            "ajuda_anexar_exame",
            "Abra Organizar exames, selecione PDFs ou fotos e confira todas as páginas, título, data e tipo. Gere o PDF com índice e salve fora do aplicativo. Esta área é temporária: as cópias expiram em 24 horas. Não há biblioteca nem envio ao servidor. Esta conversa não importa documentos por você."
        )
        if (exam.containsMatchIn(input) && missing.containsMatchIn(input)) return NluOutput(
            "ajuda_exame_sem_dados",
            "Abra Organizar exames, selecione PDFs ou fotos e confira todas as páginas, título, data e tipo. Gere o PDF com índice e salve fora do aplicativo. Esta área é temporária: as cópias expiram em 24 horas. Não há biblioteca nem envio ao servidor. Esta conversa não importa documentos por você."
        )
        if (record.containsMatchIn(input) && correction.containsMatchIn(input) && !clinicalExplanation.containsMatchIn(input)) return NluOutput(
            "ajuda_corrigir_registro",
            if (Regex("""\b(agua|hidratacao)\b""").containsMatchIn(input))
                "Se ainda não salvou, corrija a quantidade na tela antes de confirmar. Na tela de hidratação, Desfazer remove o último registro de água; confira se ele é o registro que deseja remover. Não alterei nenhum dado por esta conversa."
            else "Se está na confirmação da medição, toque em Corrigir, revise o valor e só então confirme. Para pressão e glicemia já salvas, essa tela não oferece edição de registros anteriores. Não alterei nem substituí seu registro por esta conversa."
        )
        if (record.containsMatchIn(input) && missing.containsMatchIn(input)) return NluOutput(
            "ajuda_registro_sem_dados",
            "Confira se está na conta correta e se a medição foi salva. Um período sem anotações não terá valores para mostrar. Não consigo afirmar que um registro foi perdido; confira também a tela desses dados."
        )
        return null
    }

    private data class Help(val intent: String, val pattern: Regex, val reply: String)
    private val help = listOf(
        Help("ajuda_medicao_pressao", Regex("""^(?:como (?:eu |posso |devo )?(?:medir|meco|medir corretamente)|qual (?:e )?o jeito certo de medir) (?:a |minha |a minha )?pressao(?: arterial)?$"""),
            // AHA: https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/monitoring-your-blood-pressure-at-home
            "Para medir a pressão, descanse sentado por pelo menos 5 minutos, com costas apoiadas, pés no chão e braço apoiado na altura do coração. Evite café, cigarro e exercício nos 30 minutos anteriores. Use um aparelho de braço adequado e siga as instruções dele. O app guarda o registro; ele não mede sua pressão."),
        Help("ajuda_historico_app", Regex("""^(?:onde (?:eu )?(?:vejo|encontro|consulto)|como (?:eu )?(?:vejo|consulto|acesso)) (?:o |meu |o meu )?(?:historico|registros)(?: de (?:pressao|glicemia|glicose|agua|hidratacao))?(?: no app| no aplicativo)?$"""),
            "Você pode me pedir sua última pressão, sua glicemia de ontem ou quanto bebeu de água hoje. Para um relatório em PDF, use a tela Dados e Relatórios e toque em Baixar Relatório em PDF."),
        Help("ajuda_relatorio_app", Regex("""^(?:como (?:eu |posso )?(?:gero|gerar|baixo|baixar|exporto|exportar|compartilho|compartilhar)|onde (?:eu )?(?:baixo|encontro)) (?:o |meu |um |o meu )?relatorio(?: em pdf| medico| de saude)?(?: no app| no aplicativo)?$"""),
            "Abra Dados e Relatórios e toque em Baixar Relatório em PDF. O relatório reúne os últimos 30 dias. Depois de gerar o arquivo, escolha um leitor de PDF para abrir. Para compartilhar, use a opção de compartilhar do leitor, se estiver disponível."),
        Help("ajuda_meta_agua", Regex("""^(?:como (?:eu |posso )?(?:mudo|mudar|altero|alterar|edito|editar|ajusto|ajustar)|onde (?:eu )?(?:mudo|altero|edito)) (?:a |minha |a minha )?meta (?:de agua|de hidratacao)(?: no app| no aplicativo)?$"""),
            "Na tela de hidratação, toque em Editar meta de hidratação, ajuste o valor e toque em Salvar Meta. Se você tem uma restrição de líquidos, siga a meta orientada pelo profissional que acompanha você."),
        Help("ajuda_horario_remedio", Regex("""^(?:como (?:eu |posso )?(?:mudo|mudar|altero|alterar|edito|editar|ajusto|ajustar)|onde (?:eu )?(?:mudo|altero|edito)) (?:o |meu |o meu )?horario (?:do |de |do meu )?(?:remedio|medicamento|lembrete)(?: no app| no aplicativo)?$"""),
            "Para editar horários de medicamentos, use a tela Remédios no menu superior. Confira o medicamento e o horário antes de salvar e mantenha a orientação da sua receita. A tela de lembretes informa os avisos da rotina."),
        Help("ajuda_registro_sem_dados", Regex("""^(?:por que|porque) (?:nao aparece|nao apareceu|nao encontro|nao tem) (?:o |meu |o meu )?(?:registro|historico|pressao|glicemia|glicose)(?: no app| no aplicativo)?$"""),
            "Confira se está na conta correta e se a medição foi salva. Um período sem anotações não terá valores para mostrar. Não consigo afirmar que um registro foi perdido; confira também a tela desses dados.")
    )

    fun answer(text: String, recent: HealthQuery? = null): NluOutput? {
        val normalized = BragaRoutingPolicy.normalize(text).trimEnd('.', '!', '?', ' ')
        val input = BragaDialogueRequest.main(text)
        BragaAppHelp.answer(input)?.let { return it }
        if (Regex("""^(?:lembrete de (?:remedio|medicamento)|como (?:criar|configurar) (?:um )?lembrete de (?:remedio|medicamento))$""").matches(input)) return NluOutput(
            "ajuda_horario_remedio", "Abra Remédios no menu superior para conferir o medicamento cadastrado e seus horários. Os avisos da rotina aparecem em Lembretes. Mantenha os horários orientados na sua receita.")
        if (text.contains('?') && Regex("""^(?:eu )?(?:ja )?tomei (?:o )?(?:meu )?(?:remedio|medicamento) (?:hoje|ontem)$""").matches(input)) return NluOutput(
            "entrada_medicamento_sem_nome", "Qual é o nome exato do medicamento cadastrado em Remédios? Posso consultar as doses registradas; uma anotação não confirma, por si só, que o medicamento foi tomado.")
        if (Regex("""^(?:quanto (?:remedio|medicamento) ainda tenho|(?:ja )?registrei (?:a |uma )?dose (?:do meu remedio |do remedio )?(?:de )?(?:hoje|ontem))$""").matches(input)) return NluOutput(
            "entrada_medicamento_sem_nome", "Qual é o nome exato do medicamento cadastrado em Remédios? Preciso identificar o medicamento para consultar estoque ou registros de dose.")
        if (Regex("""^(?:qual (?:e )?(?:a )?minha meta(?: e quanto falta)?|quanto falta para (?:a |minha )?meta)$""").matches(input)) return if (recent?.metric == HealthMetric.WATER)
            NluOutput(BragaHealthMemory.WATER, null, healthQuery = HealthQuery(HealthMetric.WATER, HealthPeriod.TODAY, HealthOperation.SUMMARY))
            else NluOutput("entrada_meta_ambigua", "Você quer consultar a meta de água ou a meta de passos? Diga qual meta deseja conferir.")
        operational(input)?.let { return it }
        // Não descartar a reparação por causa do agradecimento; continuidade completa vem depois.
        if (input != normalized && repair.matches(input)) return NluOutput(
            "entrada_explicacao_sem_referencia",
            "Qual informação você quer que eu explique melhor: uma medição, um registro ou uma função do aplicativo?"
        )
        help.firstOrNull { it.pattern.matches(input) }?.let {
            return NluOutput(it.intent, it.reply)
        }
        if (!variation.matches(input)) return null
        val metric = when {
            pressure.containsMatchIn(input) -> HealthMetric.PRESSURE
            glucose.containsMatchIn(input) -> HealthMetric.GLUCOSE
            else -> recent?.metric
        }
        val reply = when (metric) {
            // AHA: https://www.heart.org/en/health-topics/high-blood-pressure/blood-pressure-explained
            HealthMetric.PRESSURE -> "A pressão pode variar com estresse, café, exercício e a forma de medir. Só o registro não permite saber a causa no seu caso, nem confirmar uma mudança em relação ao anterior. Meça em repouso e leve as anotações ao profissional que acompanha você se a mudança persistir. Não altere seus remédios por conta própria."
            // CDC: https://www.cdc.gov/diabetes/living-with/10-things-that-spike-blood-sugar.html
            HealthMetric.GLUCOSE -> "A glicemia pode variar com alimentação, atividade física, estresse e doenças. O horário e o contexto da medição também importam. Só o registro não permite identificar a causa no seu caso, nem confirmar uma mudança em relação ao anterior. Compare medições feitas em condições semelhantes e converse com o profissional que acompanha você. Não ajuste medicamentos por conta própria."
            else -> return NluOutput("entrada_variacao_sem_referencia",
                "Você está falando da pressão ou da glicemia? Diga qual medida mudou para eu explicar as possibilidades.")
        }
        return NluOutput("explicacao_variacao_${if (metric == HealthMetric.PRESSURE) "pressao" else "glicemia"}", reply)
    }
}
