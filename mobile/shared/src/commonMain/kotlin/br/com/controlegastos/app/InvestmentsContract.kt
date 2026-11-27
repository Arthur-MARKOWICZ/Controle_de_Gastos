package br.com.controlegastos.app

/**
 * Contrato de investimentos (Carteira) no módulo comum (ADR-0034).
 *
 * Dinheiro segue [Money] (string decimal de duas casas na API, ADR-0006).
 * Isolado dos contratos de verbas e orçamento.
 */
interface InvestmentsGateway {
    suspend fun loadSummary(): PortfolioSummaryView
    suspend fun registerStockLot(input: NewStockLot): StockLotView
    suspend fun deleteStockLot(id: String)
    suspend fun registerCdb(input: NewCdbPosition): CdbPositionView
    suspend fun deleteCdb(id: String)
}

data class PortfolioSummaryView(
    val complete: Boolean,
    val totalCost: Money,
    val totalCurrent: Money,
    val totalPnl: Money,
    val stocks: List<PortfolioStockPositionView>,
    val cdbs: List<PortfolioCdbPositionView>,
)

data class PortfolioStockPositionView(
    val ticker: String,
    val quantity: Long,
    val averagePrice: Money,
    val acquisitionCost: Money,
    val currentValue: Money?,
    val pnl: Money?,
    val lots: List<PortfolioStockLotDetailView>,
)

data class PortfolioStockLotDetailView(
    val id: String,
    val quantity: Long,
    val unitPrice: Money,
    val acquisitionCost: Money,
    val purchaseDate: String,
    val currentValue: Money?,
    val pnl: Money?,
)

data class PortfolioCdbPositionView(
    val id: String,
    val principal: Money,
    val estimatedValue: Money,
    val pnl: Money,
)

data class NewStockLot(
    val ticker: String,
    val quantity: Long,
    val unitPrice: Money,
    val purchaseDate: String,
)

data class StockLotView(
    val id: String,
    val ticker: String,
    val quantity: Long,
    val unitPrice: Money,
    val acquisitionCost: Money,
    val purchaseDate: String,
    val createdAt: String,
    val currentValue: Money?,
)

data class NewCdbPosition(
    val principal: Money,
    val annualRate: String,
    val startDate: String,
    val maturityDate: String,
)

data class CdbPositionView(
    val id: String,
    val principal: Money,
    val annualRate: String,
    val startDate: String,
    val maturityDate: String,
    val estimatedValue: Money,
    val createdAt: String,
)
