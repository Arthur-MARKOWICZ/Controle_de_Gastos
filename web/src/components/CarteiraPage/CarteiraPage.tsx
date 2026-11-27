"use client";

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import {
  ApiError,
  createApiClient,
  type PortfolioCdbPositionDTO,
  type PortfolioStockPositionDTO,
  type PortfolioSummaryDTO,
} from "../../lib/api";
import { todaySaoPaulo } from "../../lib/dates";
import { formatBRL, formatBRLInputMask, isValidBRL, maskToPlain } from "../../lib/money";
import { AppShell } from "../AppShell/AppShell";
import { Button } from "../ui/Button";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Dialog } from "../ui/Dialog";
import { Field } from "../ui/Field";
import { Notice } from "../ui/Notice";
import { PageHeader } from "../ui/PageHeader";
import { useToast } from "../ui/ToastProvider";
import styles from "./CarteiraPage.module.css";

type Props = { email: string; onLogout(): void };

type DeleteTarget =
  | { kind: "stock"; id: string; label: string }
  | { kind: "cdb"; id: string; label: string };

function pnlToneClass(amount: string): string {
  if (amount.startsWith("-")) return styles.negative;
  if (amount === "0.00") return "";
  return styles.positive;
}

function normalizeMoneyInput(raw: string): string {
  const trimmed = raw.trim();
  if (isValidBRL(trimmed)) return trimmed;
  const fromMask = maskToPlain(trimmed);
  if (isValidBRL(fromMask)) return fromMask;
  return trimmed.replace(/\./g, "").replace(",", ".");
}

/** Converte taxa percentual anual (ex.: "12,50" -> "0.1250") para o formato decimal da API. */
function parseAnnualRatePercentToDecimal(raw: string): string | null {
  const cleaned = raw.trim().replace("%", "").replace(",", ".");
  if (!cleaned || !/^\d+(\.\d+)?$/.test(cleaned)) return null;
  const pct = Number(cleaned);
  if (!Number.isFinite(pct) || pct <= 0 || pct > 1000) return null;
  return (pct / 100).toFixed(4);
}

export function CarteiraPage({ email, onLogout }: Props) {
  const { client } = useAuth();
  const { showToast } = useToast();
  const api = useMemo(() => createApiClient(client), [client]);
  const [summary, setSummary] = useState<PortfolioSummaryDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const [stockDialogOpen, setStockDialogOpen] = useState(false);
  const [stockTicker, setStockTicker] = useState("");
  const [stockQuantity, setStockQuantity] = useState("");
  const [stockUnitPrice, setStockUnitPrice] = useState("");
  const [stockPurchaseDate, setStockPurchaseDate] = useState("");
  const [stockError, setStockError] = useState<string | null>(null);
  const [stockBusy, setStockBusy] = useState(false);

  const [cdbDialogOpen, setCdbDialogOpen] = useState(false);
  const [cdbPrincipal, setCdbPrincipal] = useState("");
  const [cdbAnnualRate, setCdbAnnualRate] = useState("");
  const [cdbStartDate, setCdbStartDate] = useState("");
  const [cdbMaturityDate, setCdbMaturityDate] = useState("");
  const [cdbError, setCdbError] = useState<string | null>(null);
  const [cdbBusy, setCdbBusy] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [assetTab, setAssetTab] = useState<"ALL" | "STOCKS" | "CDBS">("ALL");

  const loadSummary = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getPortfolioSummary();
      setSummary(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível carregar sua carteira.");
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    let cancelled = false;
    api
      .getPortfolioSummary()
      .then((data) => {
        if (!cancelled) {
          setSummary(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : "Não foi possível carregar sua carteira.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [api]);

  function openStockDialog() {
    setStockTicker("");
    setStockQuantity("");
    setStockUnitPrice("");
    setStockPurchaseDate(todaySaoPaulo());
    setStockError(null);
    setStockDialogOpen(true);
  }

  function openCdbDialog() {
    setCdbPrincipal("");
    setCdbAnnualRate("");
    setCdbStartDate(todaySaoPaulo());
    setCdbMaturityDate("");
    setCdbError(null);
    setCdbDialogOpen(true);
  }

  async function submitStock(event: FormEvent) {
    event.preventDefault();
    const quantity = Number.parseInt(stockQuantity.trim(), 10);
    const amount = normalizeMoneyInput(stockUnitPrice);
    if (!Number.isInteger(quantity) || quantity <= 0) {
      setStockError("Informe uma quantidade inteira positiva.");
      return;
    }
    if (!isValidBRL(amount)) {
      setStockError("Informe o preço unitário em reais (ex.: 10,00).");
      return;
    }
    setStockBusy(true);
    setStockError(null);
    try {
      await api.registerStockLot({
        ticker: stockTicker.trim().toUpperCase(),
        quantity,
        unitPrice: { amount, currency: "BRL" },
        purchaseDate: stockPurchaseDate.trim(),
      });
      setStockDialogOpen(false);
      const msg = "Lote de ação cadastrado na sua carteira.";
      setFeedback(msg);
      showToast(msg);
      await loadSummary();
    } catch (err) {
      setStockError(err instanceof ApiError ? err.message : "Não foi possível cadastrar o lote de ação.");
    } finally {
      setStockBusy(false);
    }
  }

  async function submitCdb(event: FormEvent) {
    event.preventDefault();
    const amount = normalizeMoneyInput(cdbPrincipal);
    if (!isValidBRL(amount)) {
      setCdbError("Informe o valor aplicado em reais (ex.: 10.000,00).");
      return;
    }
    const annualRate = parseAnnualRatePercentToDecimal(cdbAnnualRate);
    if (!annualRate) {
      setCdbError("Informe uma taxa anual válida em percentual (ex.: 12,50).");
      return;
    }
    setCdbBusy(true);
    setCdbError(null);
    try {
      await api.registerCdb({
        principal: { amount, currency: "BRL" },
        annualRate,
        startDate: cdbStartDate.trim(),
        maturityDate: cdbMaturityDate.trim(),
      });
      setCdbDialogOpen(false);
      const msg = "CDB cadastrado na sua carteira.";
      setFeedback(msg);
      showToast(msg);
      await loadSummary();
    } catch (err) {
      setCdbError(err instanceof ApiError ? err.message : "Não foi possível cadastrar o CDB.");
    } finally {
      setCdbBusy(false);
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    setDeleteBusy(true);
    setError(null);
    try {
      if (deleteTarget.kind === "stock") {
        await api.deleteStockLot(deleteTarget.id);
        const msg = "Lote de ação excluído da sua carteira.";
        setFeedback(msg);
        showToast(msg);
      } else {
        await api.deleteCdb(deleteTarget.id);
        const msg = "CDB excluído da sua carteira.";
        setFeedback(msg);
        showToast(msg);
      }
      setDeleteTarget(null);
      await loadSummary();
    } catch (err) {
      setDeleteTarget(null);
      setError(err instanceof ApiError ? err.message : "Não foi possível excluir a posição.");
    } finally {
      setDeleteBusy(false);
    }
  }

  const isEmpty = summary !== null && summary.stocks.length === 0 && summary.cdbs.length === 0;

  return (
    <AppShell current="carteira" email={email} onLogout={onLogout}>
      <div className={styles.page}>
        <PageHeader
          eyebrow="Patrimônio próprio"
          title="Carteira"
          description="Acompanhe suas posições de ações e CDBs separadamente do orçamento mensal."
          actions={
            <div className={styles.headerActions}>
              <Button variant="primary" onClick={openStockDialog}>
                Cadastrar ação
              </Button>
              <Button variant="secondary" onClick={openCdbDialog}>
                Cadastrar CDB
              </Button>
            </div>
          }
        />

        {feedback && (
          <Notice tone="success" onClose={() => setFeedback(null)}>
            {feedback}
          </Notice>
        )}

        {loading && !summary && (
          <p className={styles.status} role="status">
            Carregando carteira…
          </p>
        )}

        {error && (
          <Notice tone="danger">
            {error}{" "}
            <Button variant="text" onClick={() => void loadSummary()}>
              Tentar novamente
            </Button>
          </Notice>
        )}

        {summary && !summary.complete && (
          <Notice tone="warning">
            <strong>Resumo parcial:</strong> uma ou mais ações ainda não têm cotação disponível. Posições sem cotação
            não entram no valor atual.
          </Notice>
        )}

        {isEmpty && (
          <section className={styles.empty} aria-label="Carteira vazia">
            <h2>Ainda não há posições cadastradas</h2>
            <p>
              Comece registrando um lote de ação B3 ou um CDB prefixado. Os registros da carteira não criam lançamentos
              nas suas verbas mensais.
            </p>
            <div className={styles.emptyActions}>
              <Button variant="primary" onClick={openStockDialog}>
                Cadastrar ação
              </Button>
              <Button variant="secondary" onClick={openCdbDialog}>
                Cadastrar CDB
              </Button>
            </div>
          </section>
        )}

        {summary && !isEmpty && (() => {
          const stocksTotal = summary.stocks.reduce(
            (acc, s) => acc + Number(s.currentValue?.amount ?? s.acquisitionCost.amount),
            0,
          );
          const cdbsTotal = summary.cdbs.reduce((acc, c) => acc + Number(c.estimatedValue.amount), 0);
          const allocTotal = stocksTotal + cdbsTotal;
          const stocksPct = allocTotal > 0 ? Math.round((stocksTotal / allocTotal) * 100) : 0;
          const cdbsPct = allocTotal > 0 ? 100 - stocksPct : 0;
          const showStocks = (assetTab === "ALL" || assetTab === "STOCKS") && summary.stocks.length > 0;
          const showCdbs = (assetTab === "ALL" || assetTab === "CDBS") && summary.cdbs.length > 0;

          return (
          <>
            <div className={styles.heroGrid}>
              <section className={styles.totals} aria-label="Totais da carteira">
                <div className={styles.metric}>
                  <span className={styles.metricLabel}>Custo total</span>
                  <strong className={styles.metricValue}>{formatBRL(summary.totalCost.amount)}</strong>
                </div>
                <div className={styles.metric}>
                  <span className={styles.metricLabel}>
                    Valor atual {!summary.complete && <span className={styles.badge}>Parcial</span>}
                  </span>
                  <strong className={styles.metricValue}>{formatBRL(summary.totalCurrent.amount)}</strong>
                </div>
                <div className={styles.metric}>
                  <span className={styles.metricLabel}>Resultado (P&amp;L)</span>
                  <strong className={`${styles.metricValue} ${pnlToneClass(summary.totalPnl.amount)}`}>
                    {formatBRL(summary.totalPnl.amount)}
                  </strong>
                </div>
              </section>

              <section className={styles.allocationCard} aria-label="Alocação por classe">
                <svg className={styles.donutSvg} width="76" height="76" viewBox="0 0 36 36" aria-hidden="true">
                  <circle cx="18" cy="18" r="14" fill="none" stroke="var(--surface-muted)" strokeWidth="5" />
                  {stocksPct > 0 && (
                    <circle
                      cx="18"
                      cy="18"
                      r="14"
                      fill="none"
                      stroke="var(--purpose-limit)"
                      strokeWidth="5"
                      strokeDasharray={`${stocksPct} ${100 - stocksPct}`}
                      transform="rotate(-90 18 18)"
                    />
                  )}
                  {cdbsPct > 0 && (
                    <circle
                      cx="18"
                      cy="18"
                      r="14"
                      fill="none"
                      stroke="var(--purpose-goal)"
                      strokeWidth="5"
                      strokeDasharray={`${cdbsPct} ${100 - cdbsPct}`}
                      strokeDashoffset={-stocksPct}
                      transform="rotate(-90 18 18)"
                    />
                  )}
                </svg>
                <div className={styles.allocationLegend}>
                  <strong>Alocação</strong>
                  <span><i className={styles.legendDotStocks} aria-hidden="true" /> Ações B3: {stocksPct}%</span>
                  <span><i className={styles.legendDotCdbs} aria-hidden="true" /> Renda Fixa (CDB): {cdbsPct}%</span>
                </div>
              </section>
            </div>

            <div className={styles.assetTabs} role="tablist" aria-label="Filtrar por classe de ativo">
              <button
                type="button"
                role="tab"
                className={styles.assetTab}
                aria-selected={assetTab === "ALL"}
                onClick={() => setAssetTab("ALL")}
              >
                Tudo ({summary.stocks.length + summary.cdbs.length})
              </button>
              <button
                type="button"
                role="tab"
                className={styles.assetTab}
                aria-selected={assetTab === "STOCKS"}
                onClick={() => setAssetTab("STOCKS")}
              >
                Ações B3 ({summary.stocks.length})
              </button>
              <button
                type="button"
                role="tab"
                className={styles.assetTab}
                aria-selected={assetTab === "CDBS"}
                onClick={() => setAssetTab("CDBS")}
              >
                CDBs ({summary.cdbs.length})
              </button>
            </div>

            {showStocks && (
              <section className={styles.section} aria-labelledby="carteira-acoes-heading">
                <div className={styles.sectionHeader}>
                  <h2 id="carteira-acoes-heading">Ações</h2>
                  <span className={styles.sectionNote}>Posições agregadas por ticker B3</span>
                </div>
                <ul className={styles.cardList}>
                  {summary.stocks.map((stock: PortfolioStockPositionDTO) => (
                    <li key={stock.ticker} className={styles.card}>
                      <div className={styles.cardTop}>
                        <div>
                          <p className={styles.ticker}>{stock.ticker}</p>
                          <p className={styles.meta}>
                            {stock.quantity} {stock.quantity === 1 ? "ação" : "ações"} · Preço médio{" "}
                            {formatBRL(stock.averagePrice.amount)}
                          </p>
                        </div>
                      </div>
                      <dl className={styles.kvGrid}>
                        <div className={styles.kv}>
                          <dt>Custo de aquisição</dt>
                          <dd>{formatBRL(stock.acquisitionCost.amount)}</dd>
                        </div>
                        <div className={styles.kv}>
                          <dt>Valor atual</dt>
                          <dd>
                            {stock.currentValue ? (
                              formatBRL(stock.currentValue.amount)
                            ) : (
                              <span className={styles.muted}>Sem cotação</span>
                            )}
                          </dd>
                        </div>
                        <div className={styles.kv}>
                          <dt>Resultado</dt>
                          <dd className={stock.pnl ? pnlToneClass(stock.pnl.amount) : styles.muted}>
                            {stock.pnl ? formatBRL(stock.pnl.amount) : "Sem cotação"}
                          </dd>
                        </div>
                      </dl>
                      <div className={styles.tableWrap}>
                        <table className={styles.lotsTable} aria-label={`Lotes de ${stock.ticker}`}>
                          <thead>
                            <tr>
                              <th>Compra</th>
                              <th className={styles.numCol}>Qtd</th>
                              <th className={styles.numCol}>Preço unit.</th>
                              <th className={styles.numCol}>Custo</th>
                              <th className={styles.numCol}>Ação</th>
                            </tr>
                          </thead>
                          <tbody>
                            {stock.lots.map((lot) => (
                              <tr key={lot.id}>
                                <td>{lot.purchaseDate}</td>
                                <td className={styles.numCol}>{lot.quantity}</td>
                                <td className={styles.numCol}>{formatBRL(lot.unitPrice.amount)}</td>
                                <td className={styles.numCol}>{formatBRL(lot.acquisitionCost.amount)}</td>
                                <td className={styles.numCol}>
                                  <Button
                                    variant="text"
                                    onClick={() =>
                                      setDeleteTarget({
                                        kind: "stock",
                                        id: lot.id,
                                        label: `o lote de ${stock.ticker} (${lot.quantity} em ${lot.purchaseDate})`,
                                      })
                                    }
                                  >
                                    Excluir lote
                                  </Button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {showCdbs && (
              <section className={styles.section} aria-labelledby="carteira-cdbs-heading">
                <div className={styles.sectionHeader}>
                  <h2 id="carteira-cdbs-heading">CDBs</h2>
                  <span className={styles.sectionNote}>Valor bruto estimado (sem desconto de IR/IOF)</span>
                </div>
                <ul className={styles.cardList}>
                  {summary.cdbs.map((cdb: PortfolioCdbPositionDTO, index: number) => (
                    <li key={cdb.id} className={styles.card}>
                      <div className={styles.cardTop}>
                        <div>
                          <p className={styles.ticker}>CDB #{index + 1}</p>
                          <p className={styles.meta}>
                            <span className={styles.badge}>Estimado</span> Bruto, sem IR/IOF
                          </p>
                        </div>
                        <Button
                          variant="text"
                          onClick={() =>
                            setDeleteTarget({
                              kind: "cdb",
                              id: cdb.id,
                              label: `o CDB #${index + 1} (principal ${formatBRL(cdb.principal.amount)})`,
                            })
                          }
                        >
                          Excluir CDB
                        </Button>
                      </div>
                      <dl className={styles.kvGrid}>
                        <div className={styles.kv}>
                          <dt>Principal aplicado</dt>
                          <dd>{formatBRL(cdb.principal.amount)}</dd>
                        </div>
                        <div className={styles.kv}>
                          <dt>Valor estimado</dt>
                          <dd>{formatBRL(cdb.estimatedValue.amount)}</dd>
                        </div>
                        <div className={styles.kv}>
                          <dt>Resultado estimado</dt>
                          <dd className={pnlToneClass(cdb.pnl.amount)}>{formatBRL(cdb.pnl.amount)}</dd>
                        </div>
                      </dl>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </>
          );
        })()}

        <Dialog open={stockDialogOpen} onClose={() => setStockDialogOpen(false)} title="Cadastrar ação">
          <form className={styles.form} onSubmit={(event) => void submitStock(event)}>
            {stockError && <Notice tone="danger">{stockError}</Notice>}
            <Field id="stock-ticker" label="Ticker B3" hint="Ex.: PETR4, VALE3, ITUB4">
              <input
                type="text"
                value={stockTicker}
                onChange={(event) => setStockTicker(event.target.value)}
                required
              />
            </Field>
            <Field id="stock-quantity" label="Quantidade">
              <input
                type="number"
                min="1"
                step="1"
                value={stockQuantity}
                onChange={(event) => setStockQuantity(event.target.value)}
                required
              />
            </Field>
            <Field id="stock-unit-price" label="Preço unitário (BRL)" hint="Use vírgula para centavos (ex.: 10,00).">
              <input
                type="text"
                inputMode="decimal"
                value={stockUnitPrice}
                placeholder="0,00"
                onChange={(event) => setStockUnitPrice(formatBRLInputMask(event.target.value))}
                required
              />
            </Field>
            <Field id="stock-purchase-date" label="Data de compra">
              <input
                type="date"
                value={stockPurchaseDate}
                onChange={(event) => setStockPurchaseDate(event.target.value)}
                required
              />
            </Field>
            <div className={styles.formActions}>
              <Button variant="secondary" onClick={() => setStockDialogOpen(false)} disabled={stockBusy}>
                Cancelar
              </Button>
              <Button type="submit" variant="primary" busy={stockBusy}>
                Salvar ação
              </Button>
            </div>
          </form>
        </Dialog>

        <Dialog open={cdbDialogOpen} onClose={() => setCdbDialogOpen(false)} title="Cadastrar CDB">
          <form className={styles.form} onSubmit={(event) => void submitCdb(event)}>
            {cdbError && <Notice tone="danger">{cdbError}</Notice>}
            <Field id="cdb-principal" label="Valor aplicado (BRL)" hint="Use vírgula para centavos (ex.: 10.000,00).">
              <input
                type="text"
                inputMode="decimal"
                value={cdbPrincipal}
                placeholder="0,00"
                onChange={(event) => setCdbPrincipal(formatBRLInputMask(event.target.value))}
                required
              />
            </Field>
            <Field
              id="cdb-annual-rate"
              label="Taxa anual (% a.a.)"
              hint="Informe em percentual (ex.: 12,50 para 12,50% ao ano)."
            >
              <input
                type="text"
                inputMode="decimal"
                value={cdbAnnualRate}
                placeholder="12,50"
                onChange={(event) => setCdbAnnualRate(event.target.value)}
                required
              />
            </Field>
            <Field id="cdb-start-date" label="Data de início">
              <input
                type="date"
                value={cdbStartDate}
                onChange={(event) => setCdbStartDate(event.target.value)}
                required
              />
            </Field>
            <Field id="cdb-maturity-date" label="Data de vencimento">
              <input
                type="date"
                value={cdbMaturityDate}
                onChange={(event) => setCdbMaturityDate(event.target.value)}
                required
              />
            </Field>
            <div className={styles.formActions}>
              <Button variant="secondary" onClick={() => setCdbDialogOpen(false)} disabled={cdbBusy}>
                Cancelar
              </Button>
              <Button type="submit" variant="primary" busy={cdbBusy}>
                Salvar CDB
              </Button>
            </div>
          </form>
        </Dialog>

        <ConfirmDialog
          open={deleteTarget !== null}
          title="Excluir posição"
          message={
            deleteTarget
              ? `Tem certeza que deseja excluir ${deleteTarget.label}? Esta ação não pode ser desfeita.`
              : ""
          }
          confirmLabel="Excluir"
          onConfirm={() => void confirmDelete()}
          onCancel={() => setDeleteTarget(null)}
          busy={deleteBusy}
        />
      </div>
    </AppShell>
  );
}
