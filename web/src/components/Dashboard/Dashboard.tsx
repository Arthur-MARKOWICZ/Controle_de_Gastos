import styles from "./Dashboard.module.css";

const envelopes = [
  { name: "Combustível", detail: "Limite de gasto", value: "R$ 240,00", meta: "de R$ 400,00", progress: 60, status: "Dentro do limite" },
  { name: "Investimentos", detail: "Meta de aporte", value: "R$ 1.200,00", meta: "de R$ 1.500,00", progress: 80, status: "R$ 300,00 para a meta" },
  { name: "Livros", detail: "Saldo acumulado", value: "R$ 180,00", meta: "disponíveis", progress: 45, status: "Acumulou por 2 meses" },
];

const activities = [
  { label: "Posto Avenida", envelope: "Combustível", amount: "− R$ 120,00" },
  { label: "Aporte mensal", envelope: "Investimentos", amount: "+ R$ 500,00" },
  { label: "Doação mensal", envelope: "Doação", amount: "− R$ 100,00" },
];

export function Dashboard({ email, onLogout }: { email: string; onLogout(): void }) {
  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar} aria-label="Navegação principal">
        <a className={styles.brand} href="#conteudo"><span aria-hidden="true" className={styles.brandMark}>V</span><span>Verbas</span></a>
        <nav><ul className={styles.navList}>
          <li><a href="#visao-geral" aria-current="page">Visão geral</a></li><li><a href="#verbas">Verbas</a></li><li><a href="#historico">Histórico</a></li><li><a href="#relatorios">Relatórios</a></li>
        </ul></nav>
        <div className={styles.sidebarFooter}>
          <span className={styles.avatar} aria-hidden="true">{email.slice(0, 1).toUpperCase()}</span>
          <span><strong>{email}</strong><button type="button" onClick={onLogout}>Sair da conta</button></span>
        </div>
      </aside>
      <main id="conteudo" className={styles.main}>
        <p className={styles.demoNotice} role="status">Estrutura visual com dados demonstrativos</p>
        <header className={styles.pageHeader}><div><p className={styles.eyebrow}>Agosto de 2026</p><h1>Visão geral</h1><p>Veja o que já está reservado antes de decidir o próximo gasto.</p></div><a className={styles.primaryAction} href="#nova-verba">Nova verba</a></header>
        <section id="visao-geral" className={styles.summary} aria-labelledby="resumo-titulo">
          <div className={styles.summaryLead}><h2 id="resumo-titulo">Renda do mês</h2><strong>R$ 5.000,00</strong><span>Renda fixa atual</span></div>
          <dl className={styles.summaryStats}><div><dt>Já reservado</dt><dd>R$ 4.250,00</dd></div><div><dt>Não alocado</dt><dd>R$ 750,00</dd></div><div><dt>Uso da renda</dt><dd>85%</dd></div></dl>
        </section>
        <div className={styles.contentGrid}>
          <section id="verbas" className={styles.panel} aria-labelledby="verbas-titulo">
            <div className={styles.panelHeader}><div><h2 id="verbas-titulo">Suas verbas</h2><p>Valores disponíveis neste mês</p></div><a href="#todas-verbas">Ver todas</a></div>
            <ul className={styles.envelopeList}>{envelopes.map((envelope) => <li key={envelope.name}>
              <div className={styles.envelopeHeading}><div><h3>{envelope.name}</h3><span>{envelope.detail}</span></div><div className={styles.envelopeValue}><strong>{envelope.value}</strong><span>{envelope.meta}</span></div></div>
              <progress className={styles.progressTrack} aria-label={`Progresso de ${envelope.name}`} value={envelope.progress} max={100} /><p className={styles.statusText}>{envelope.status}</p>
            </li>)}</ul>
          </section>
          <section id="historico" className={styles.panel} aria-labelledby="historico-titulo">
            <div className={styles.panelHeader}><div><h2 id="historico-titulo">Atividade recente</h2><p>Últimos lançamentos manuais</p></div><a href="#todo-historico">Ver histórico</a></div>
            <ul className={styles.activityList}>{activities.map((activity) => <li key={activity.label}><span className={styles.activityMark} aria-hidden="true" /><div><strong>{activity.label}</strong><span>{activity.envelope}</span></div><span className={styles.activityAmount}>{activity.amount}</span></li>)}</ul>
            <a className={styles.secondaryAction} href="#novo-lancamento">Registrar gasto</a>
          </section>
        </div>
      </main>
    </div>
  );
}
