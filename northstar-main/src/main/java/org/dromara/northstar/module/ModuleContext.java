package org.dromara.northstar.module;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dromara.northstar.common.constant.Constants;
import org.dromara.northstar.common.constant.DateTimeConstant;
import org.dromara.northstar.common.constant.ModuleState;
import org.dromara.northstar.common.constant.SignalOperation;
import org.dromara.northstar.common.exception.InsufficientException;
import org.dromara.northstar.common.exception.NoSuchElementException;
import org.dromara.northstar.common.model.AccountRuntimeDescription;
import org.dromara.northstar.common.model.ContractSimpleInfo;
import org.dromara.northstar.common.model.Identifier;
import org.dromara.northstar.common.model.ModuleAccountRuntimeDescription;
import org.dromara.northstar.common.model.ModuleDealRecord;
import org.dromara.northstar.common.model.ModuleDescription;
import org.dromara.northstar.common.model.ModulePositionDescription;
import org.dromara.northstar.common.model.ModuleRuntimeDescription;
import org.dromara.northstar.common.model.TimeSeriesValue;
import org.dromara.northstar.common.model.Tuple;
import org.dromara.northstar.common.utils.BarUtils;
import org.dromara.northstar.common.utils.FieldUtils;
import org.dromara.northstar.common.utils.OrderUtils;
import org.dromara.northstar.data.IModuleRepository;
import org.dromara.northstar.gateway.Contract;
import org.dromara.northstar.gateway.IContractManager;
import org.dromara.northstar.indicator.Indicator;
import org.dromara.northstar.indicator.IndicatorValueUpdateHelper;
import org.dromara.northstar.indicator.constant.PeriodUnit;
import org.dromara.northstar.indicator.model.Configuration;
import org.dromara.northstar.strategy.IAccount;
import org.dromara.northstar.strategy.IModule;
import org.dromara.northstar.strategy.IModuleAccount;
import org.dromara.northstar.strategy.IModuleContext;
import org.dromara.northstar.strategy.OrderRequestFilter;
import org.dromara.northstar.strategy.TradeStrategy;
import org.dromara.northstar.strategy.constant.PriceType;
import org.dromara.northstar.strategy.model.TradeIntent;
import org.dromara.northstar.support.log.ModuleLoggerFactory;
import org.dromara.northstar.support.utils.bar.BarMergerRegistry;
import org.dromara.northstar.support.utils.bar.BarMergerRegistry.ListenerType;
import org.slf4j.Logger;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.protobuf.InvalidProtocolBufferException;

import cn.hutool.core.lang.Assert;
import lombok.Getter;
import lombok.Setter;
import xyz.redtorch.pb.CoreEnum.ContingentConditionEnum;
import xyz.redtorch.pb.CoreEnum.DirectionEnum;
import xyz.redtorch.pb.CoreEnum.ForceCloseReasonEnum;
import xyz.redtorch.pb.CoreEnum.HedgeFlagEnum;
import xyz.redtorch.pb.CoreEnum.OffsetFlagEnum;
import xyz.redtorch.pb.CoreEnum.OrderPriceTypeEnum;
import xyz.redtorch.pb.CoreEnum.TimeConditionEnum;
import xyz.redtorch.pb.CoreEnum.VolumeConditionEnum;
import xyz.redtorch.pb.CoreField.BarField;
import xyz.redtorch.pb.CoreField.CancelOrderReqField;
import xyz.redtorch.pb.CoreField.ContractField;
import xyz.redtorch.pb.CoreField.OrderField;
import xyz.redtorch.pb.CoreField.PositionField;
import xyz.redtorch.pb.CoreField.SubmitOrderReqField;
import xyz.redtorch.pb.CoreField.TickField;
import xyz.redtorch.pb.CoreField.TradeField;

public class ModuleContext implements IModuleContext{
	
	@Getter
	@Setter
	protected IModule module;
	
	protected Logger logger;
	
	protected TradeStrategy tradeStrategy;
	
	protected IModuleRepository moduleRepo;
	
	protected ModuleAccount moduleAccount;
	
	/* originOrderId -> orderReq */
	protected ConcurrentMap<String, SubmitOrderReqField> orderReqMap = new ConcurrentHashMap<>();
	
	/* unifiedSymbol -> contract */
	protected Map<String, ContractField> contractMap = new HashMap<>();
	protected Map<String, Contract> contractMap2 = new HashMap<>();
	
	/* unifiedSymbol -> tick */
	protected ConcurrentMap<String, TickField> latestTickMap = new ConcurrentHashMap<>();
	
	/* unifiedSymbol -> barQ */
	protected ConcurrentMap<String, Queue<BarField>> barBufQMap = new ConcurrentHashMap<>();
	
	/* indicator -> values */
	protected ConcurrentMap<Indicator, Queue<TimeSeriesValue>> indicatorValBufQMap = new ConcurrentHashMap<>(); 
	
	/* unifiedSymbol -> indicatorName -> indicator */
	protected Table<String, String, Indicator> indicatorNameTbl = HashBasedTable.create();
	
	protected Set<IndicatorValueUpdateHelper> indicatorHelperSet = new HashSet<>();
	
	/* unifiedSymbol -> tradeIntent */
	protected ConcurrentMap<String, TradeIntent> tradeIntentMap = new ConcurrentHashMap<>();	// 交易意图
	
	protected final AtomicInteger bufSize = new AtomicInteger(0);
	
	protected final BarMergerRegistry registry;
	
	protected boolean enabled;
	
	protected String tradingDay = "";
	
	protected IContractManager contractMgr;
	
	protected OrderRequestFilter orderReqFilter;
	
	public ModuleContext(TradeStrategy tradeStrategy, ModuleDescription moduleDescription, ModuleRuntimeDescription moduleRtDescription,
			IContractManager contractMgr, IModuleRepository moduleRepo, ModuleLoggerFactory loggerFactory, BarMergerRegistry barMergerRegistry) {
		this.tradeStrategy = tradeStrategy;
		this.moduleRepo = moduleRepo;
		this.contractMgr = contractMgr;
		this.registry = barMergerRegistry;
		this.logger = loggerFactory.getLogger(moduleDescription.getModuleName());
		this.bufSize.set(moduleDescription.getModuleCacheDataSize());
		this.moduleAccount = new ModuleAccount(moduleDescription, moduleRtDescription, new ModuleStateMachine(this), moduleRepo, contractMgr, logger);
		this.orderReqFilter = new DefaultOrderFilter(moduleDescription.getModuleAccountSettingsDescription().stream().flatMap(mad -> mad.getBindedContracts().stream()).toList(), this);
		moduleDescription.getModuleAccountSettingsDescription().stream()
			.forEach(mad -> {
				for(ContractSimpleInfo csi : mad.getBindedContracts()) {
					Contract contract = contractMgr.getContract(Identifier.of(csi.getValue()));
					ContractField cf = contract.contractField();
					contractMap.put(csi.getUnifiedSymbol(), cf);
					contractMap2.put(csi.getUnifiedSymbol(), contract);
					barBufQMap.put(cf.getUnifiedSymbol(), new ConcurrentLinkedQueue<>());
					registry.addListener(contract, moduleDescription.getNumOfMinPerBar(), PeriodUnit.MINUTE, tradeStrategy, ListenerType.STRATEGY);
					registry.addListener(contract, moduleDescription.getNumOfMinPerBar(), PeriodUnit.MINUTE, this, ListenerType.CONTEXT);
				}
			});
	}
	
	@Override
	public boolean explain(boolean expression, String infoMessage, Object... args) {
		if(expression) {
			getLogger().info(infoMessage, args);
		}
		return expression;
	}

	@Override
	public ContractField getContract(String unifiedSymbol) {
		if(!contractMap.containsKey(unifiedSymbol)) {
			throw new NoSuchElementException("模组没有绑定合约：" + unifiedSymbol);
		}
		return contractMap.get(unifiedSymbol);
	}

	@Override
	public void submitOrderReq(TradeIntent tradeIntent) {
		if(!module.isEnabled()) {
			if(isReady()) {
				getLogger().info("策略处于停用状态，忽略委托单");
			}
			return;
		}
		TickField tick = latestTickMap.get(tradeIntent.getContract().getUnifiedSymbol());
		if(Objects.isNull(tick)) {
			getLogger().warn("没有TICK行情数据时，忽略下单请求");
			return;
		}
		getLogger().info("收到下单意图：{}", tradeIntent);
		tradeIntentMap.put(tradeIntent.getContract().getUnifiedSymbol(), tradeIntent);
		tradeIntent.setContext(this);
        tradeIntent.onTick(tick);
	}

	@Override
	public int numOfMinPerMergedBar() {
		return module.getModuleDescription().getNumOfMinPerBar();
	}

	@Override
	public IAccount getAccount(ContractField contract) {
		if(!contractMap2.containsKey(contract.getUnifiedSymbol())) {
			throw new NoSuchElementException("模组没有绑定合约：" + contract.getUnifiedSymbol());
		}
		Contract c = contractMap2.get(contract.getUnifiedSymbol());
		return module.getAccount(c);
	}

	@Override
	public IModuleAccount getModuleAccount() {
		return moduleAccount;
	}

	@Override
	public ModuleState getState() {
		return moduleAccount.getModuleState();
	}

	@Override
	public void disabledModule() {
		getLogger().warn("策略层主动停用模组");
		setEnabled(false);
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	@Override
	public void registerIndicator(Indicator indicator) {
		checkIndicator(indicator);
		Configuration cfg = indicator.getConfiguration();
		Contract c = contractMap2.get(cfg.contract().getUnifiedSymbol());
		IndicatorValueUpdateHelper helper = new IndicatorValueUpdateHelper(indicator);
		indicatorHelperSet.add(helper);
		registry.addListener(c, cfg.numOfUnits(), cfg.period(), helper, ListenerType.INDICATOR);
	}
	
	public void checkIndicator(Indicator indicator) {
		// 递归子指标
		for(Indicator in : indicator.dependencies()) {
			checkIndicator(in);
		}
		Configuration cfg = indicator.getConfiguration();
		String unifiedSymbol = cfg.contract().getUnifiedSymbol();
		String indicatorName = String.format("%s_%d%s", cfg.indicatorName(), cfg.numOfUnits(), cfg.period().symbol());
		logger.trace("检查指标配置信息：{}", indicatorName);
		Assert.isTrue(cfg.numOfUnits() > 0, "周期数必须大于0，当前为：" + cfg.numOfUnits());
		Assert.isTrue(cfg.cacheLength() > 0, "指标回溯长度必须大于0，当前为：" + cfg.cacheLength());
		if(cfg.visible()) {		// 不显示的指标可以不做重名校验
			Assert.isTrue(!indicatorNameTbl.contains(unifiedSymbol, indicatorName) || indicator.equals(indicatorNameTbl.get(unifiedSymbol, indicatorName)), "指标 [{} -> {}] 已存在。不能重名", unifiedSymbol, indicatorName);
			indicatorNameTbl.put(unifiedSymbol, indicatorName, indicator);
		}
		indicatorValBufQMap.put(indicator, new ConcurrentLinkedDeque<>());
	}
	
	@Override
	public void onTick(TickField tick) {
		getLogger().trace("TICK信息: {} {} {} {}，最新价: {}", 
				tick.getUnifiedSymbol(), tick.getActionDay(), tick.getActionTime(), tick.getActionTimestamp(), tick.getLastPrice());
		if(tradeIntentMap.containsKey(tick.getUnifiedSymbol())) {
			TradeIntent tradeIntent = tradeIntentMap.get(tick.getUnifiedSymbol());
			tradeIntent.onTick(tick);
			if(tradeIntent.hasTerminated()) {
				tradeIntentMap.remove(tick.getUnifiedSymbol());
				getLogger().debug("移除交易意图：{}", tick.getUnifiedSymbol());
			}
		}
		if(!StringUtils.equals(tradingDay, tick.getTradingDay())) {
			tradingDay = tick.getTradingDay();
		}
		indicatorHelperSet.forEach(helper -> helper.onTick(tick));
		moduleAccount.onTick(tick);
		latestTickMap.put(tick.getUnifiedSymbol(), tick);
		tradeStrategy.onTick(tick);
	}

	@Override
	public void onBar(BarField bar) {
		getLogger().trace("分钟Bar信息: {} {} {} {}，最新价: {}", bar.getUnifiedSymbol(), bar.getActionDay(), bar.getActionTime(), bar.getActionTimestamp(), bar.getClosePrice());
		indicatorHelperSet.forEach(helper -> helper.onBar(bar));
		registry.onBar(bar);		
	}
	
	@Override
	public void onMergedBar(BarField bar) {
		getLogger().debug("合并Bar信息: {} {} {} {}，最新价: {}", bar.getUnifiedSymbol(), bar.getActionDay(), bar.getActionTime(), bar.getActionTimestamp(), bar.getClosePrice());
		try {			
			indicatorHelperSet.stream().map(IndicatorValueUpdateHelper::getIndicator).forEach(indicator -> visualize(indicator, bar));
		} catch(Exception e) {
			getLogger().error(e.getMessage(), e);
		}
		if(barBufQMap.get(bar.getUnifiedSymbol()).size() >= bufSize.intValue()) {
			barBufQMap.get(bar.getUnifiedSymbol()).poll();
		}
		barBufQMap.get(bar.getUnifiedSymbol()).offer(bar);		
		if(isEnabled()) {
			moduleRepo.saveRuntime(getRuntimeDescription(false));
		}
	}
	
	private void visualize(Indicator indicator, BarField bar) {
		for(Indicator in : indicator.dependencies()) {
			visualize(in, bar);
		}
		if(!StringUtils.equals(indicator.getConfiguration().contract().getUnifiedSymbol(), bar.getUnifiedSymbol())) {
			return;
		}
		ConcurrentLinkedDeque<TimeSeriesValue> list = (ConcurrentLinkedDeque<TimeSeriesValue>) indicatorValBufQMap.get(indicator);
		if(list.size() >= bufSize.intValue()) {
			list.poll();
		}
		if(indicator.isReady() && indicator.getConfiguration().visible() && indicator.get(0).timestamp() == bar.getActionTimestamp()
				&& (list.isEmpty() || list.peekLast().getTimestamp() != bar.getActionTimestamp())
				&& (BarUtils.isEndOfTheTradingDay(bar) || indicator.getConfiguration().ifPlotPerBar() || !indicator.get(0).unstable())) {		
			list.offer(new TimeSeriesValue(indicator.get(0).value(), bar.getActionTimestamp()));	
		}
	}
	
	@Override
	public void onOrder(OrderField order) {
		if(!orderReqMap.containsKey(order.getOriginOrderId())) {
			return;
		}
		if(!OrderUtils.isValidOrder(order) || OrderUtils.isDoneOrder(order)) {
			// 延时3秒再移除订单信息，避免移除了订单信息后，成交无法匹配的问题
			CompletableFuture.runAsync(() -> orderReqMap.remove(order.getOriginOrderId()), CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS));	
		}
		moduleAccount.onOrder(order);
		tradeStrategy.onOrder(order);
		if(tradeIntentMap.containsKey(order.getContract().getUnifiedSymbol())) {
			TradeIntent tradeIntent = tradeIntentMap.get(order.getContract().getUnifiedSymbol());
			tradeIntent.onOrder(order);
		}
	}

	@Override
	public void onTrade(TradeField trade) {
		if(!orderReqMap.containsKey(trade.getOriginOrderId()) && !StringUtils.equals(trade.getOriginOrderId(), Constants.MOCK_ORDER_ID)) {
			return;
		} 
		if(orderReqMap.containsKey(trade.getOriginOrderId()) && getLogger().isInfoEnabled()) {
			getLogger().info("成交：{}， 操作：{}{}， 价格：{}， 手数：{}", trade.getOriginOrderId(), FieldUtils.chn(trade.getDirection()), 
					FieldUtils.chn(trade.getOffsetFlag()), trade.getPrice(), trade.getVolume());
		}
		moduleAccount.onTrade(trade);
		tradeStrategy.onTrade(trade);
		moduleRepo.saveRuntime(getRuntimeDescription(false));
		
		String unifiedSymbol = trade.getContract().getUnifiedSymbol();
		if(tradeIntentMap.containsKey(unifiedSymbol)) {
			TradeIntent tradeIntent = tradeIntentMap.get(unifiedSymbol);
			tradeIntent.onTrade(trade);
			if(tradeIntent.hasTerminated()) {
				tradeIntentMap.remove(unifiedSymbol);
			}
		}
	}

	@Override
	public void initData(List<BarField> barData) {
		if(barData.isEmpty()) {
			getLogger().debug("初始化数据为空");
			return;
		}
		
		getLogger().debug("合约{} 初始化数据 {} {} -> {} {}", barData.get(0).getUnifiedSymbol(),
				barData.get(0).getActionDay(), barData.get(0).getActionTime(), 
				barData.get(barData.size() - 1).getActionDay(), barData.get(barData.size() - 1).getActionTime());
		for(BarField bar : barData) {
			onBar(bar);
		}
	}

	@Override
	public ModuleRuntimeDescription getRuntimeDescription(boolean fullDescription) {
		ModulePositionDescription posDescription = ModulePositionDescription.builder()
				.logicalPositions(moduleAccount.getPositions().stream().map(PositionField::toByteArray).toList())
				.nonclosedTrades(moduleAccount.getNonclosedTrades().stream().map(TradeField::toByteArray).toList())
				.build();
		ModuleAccountRuntimeDescription accRtDescription = ModuleAccountRuntimeDescription.builder()
				.initBalance(moduleAccount.getInitBalance())
				.accCloseProfit(moduleAccount.getAccCloseProfit())
				.accDealVolume(moduleAccount.getAccDealVolume())
				.accCommission(moduleAccount.getAccCommission())
				.maxDrawback(moduleAccount.getMaxDrawback())
				.maxDrawbackPercentage(moduleAccount.getMaxDrawbackPercentage())
				.maxProfit(moduleAccount.getMaxProfit())
				.positionDescription(posDescription)
				.availableAmount(moduleAccount.availableAmount())
				.build();
		List<AccountRuntimeDescription> accRts = contractMap.values().stream()
				.map(this::getAccount)
				.collect(Collectors.toSet())
				.stream()
				.map(acc -> AccountRuntimeDescription.builder()
						.name(acc.accountId())
						.balance(acc.accountBalance())
						.availableAmount(acc.availableAmount())
						.build())
				.toList();
		ModuleRuntimeDescription mad = ModuleRuntimeDescription.builder()
				.moduleName(module.getName())
				.enabled(module.isEnabled())
				.moduleState(moduleAccount.getModuleState())
				.storeObject(tradeStrategy.getStoreObject())
				.strategyInfos(tradeStrategy.strategyInfos())
				.moduleAccountRuntime(accRtDescription)
				.accountRuntimes(accRts)
				.build();
		
		if(fullDescription) {
			List<ModuleDealRecord> dealRecords = moduleRepo.findAllDealRecords(module.getName());
			double avgProfit = dealRecords.stream().mapToDouble(ModuleDealRecord::getDealProfit).average().orElse(0D);
			double annualizedRateOfReturn = 0;
			if(!dealRecords.isEmpty()) {
				LocalDate startDate = LocalDate.parse(parse(dealRecords.get(0).getOpenTrade()).getTradeDate(), DateTimeConstant.D_FORMAT_INT_FORMATTER);
				LocalDate endDate = LocalDate.parse(parse(dealRecords.get(dealRecords.size() - 1).getCloseTrade()).getTradeDate(), DateTimeConstant.D_FORMAT_INT_FORMATTER);
				long days = ChronoUnit.DAYS.between(startDate, endDate);
				double totalEarning = moduleAccount.getAccCloseProfit() - moduleAccount.getAccCommission();
				annualizedRateOfReturn = (totalEarning / moduleAccount.getInitBalance()) / days * 365;  
			}
			accRtDescription.setAvgEarning(avgProfit);
			accRtDescription.setAnnualizedRateOfReturn(annualizedRateOfReturn);
			
			Map<String, List<String>> indicatorMap = new HashMap<>();
			Map<String, LinkedHashMap<Long, JSONObject>> symbolTimeObject = new HashMap<>();
			barBufQMap.entrySet().forEach(e -> 
				e.getValue().forEach(bar -> {
					if(!symbolTimeObject.containsKey(bar.getUnifiedSymbol())) {
						symbolTimeObject.put(bar.getUnifiedSymbol(), new LinkedHashMap<>());
					}
					symbolTimeObject.get(bar.getUnifiedSymbol()).put(bar.getActionTimestamp(), assignBar(bar));
				})
			);
			
			indicatorValBufQMap.entrySet().forEach(e -> {
				Indicator in = e.getKey();
				String unifiedSymbol = in.getConfiguration().contract().getUnifiedSymbol();
				Configuration cfg = in.getConfiguration();
				String indicatorName = String.format("%s_%d%s", cfg.indicatorName(), cfg.numOfUnits(), cfg.period().symbol());
				if(!indicatorMap.containsKey(unifiedSymbol)) {
					indicatorMap.put(unifiedSymbol, new ArrayList<>());
				}
				if(cfg.visible()) {
					indicatorMap.get(unifiedSymbol).add(indicatorName);
				}
				Collections.sort(indicatorMap.get(unifiedSymbol));
				
				e.getValue().stream().forEach(tv -> {
					if(!symbolTimeObject.containsKey(unifiedSymbol)
							|| !symbolTimeObject.get(unifiedSymbol).containsKey(tv.getTimestamp())) {
						return;
					}
					symbolTimeObject.get(unifiedSymbol).get(tv.getTimestamp()).put(indicatorName, tv.getValue());
				});
			});
			Map<String, JSONArray> dataMap = barBufQMap.entrySet().stream().collect(Collectors.toMap(
					Entry::getKey, 
					e -> {
						if(!symbolTimeObject.containsKey(e.getKey())) 							
							return new JSONArray();
						return new JSONArray(symbolTimeObject.get(e.getKey()).values().stream().toList());
					})
			);
			
			mad.setIndicatorMap(indicatorMap);
			mad.setDataMap(dataMap);
		}
		return mad;
	}
	
	private TradeField parse(byte[] data) {
		try {
			return TradeField.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalStateException(e);
		}
	}
	
	private JSONObject assignBar(BarField bar) {
		JSONObject json = new JSONObject();
		json.put("open", bar.getOpenPrice());
		json.put("low", bar.getLowPrice());
		json.put("high", bar.getHighPrice());
		json.put("close", bar.getClosePrice());
		json.put("volume", bar.getVolume());
		json.put("openInterestDelta", bar.getOpenInterestDelta());
		json.put("openInterest", bar.getOpenInterest());
		json.put("timestamp", bar.getActionTimestamp());
		return json;
	}

	@Override
	public Optional<String> submitOrderReq(ContractField contract, SignalOperation operation, PriceType priceType, int volume, double price) {
		if(!module.isEnabled()) {
			if(isReady()) {
				getLogger().info("策略处于停用状态，忽略委托单");
			}
			return Optional.empty();
		}
		TickField tick = latestTickMap.get(contract.getUnifiedSymbol());
		Assert.notNull(tick, "没有行情时不应该发送订单");
		Assert.isTrue(volume > 0, "下单手数应该为正数。当前为" + volume);
		
		double orderPrice = priceType.resolvePrice(tick, operation, price);
		if(getLogger().isInfoEnabled()) {
			getLogger().info("[{} {}] 策略信号：合约【{}】，操作【{}】，价格【{}】，手数【{}】，类型【{}】", 
					tick.getActionDay(), LocalTime.parse(tick.getActionTime(), DateTimeConstant.T_FORMAT_WITH_MS_INT_FORMATTER),
					contract.getUnifiedSymbol(), operation.text(), orderPrice, volume, priceType);
		}
		String id = UUID.randomUUID().toString();
		String gatewayId = getAccount(contract).accountId();
		DirectionEnum direction = OrderUtils.resolveDirection(operation);
		int factor = FieldUtils.directionFactor(direction);
		double plusPrice = module.getModuleDescription().getOrderPlusTick() * contract.getPriceTick(); // 超价设置
		PositionField pos = getAccount(contract).getPosition(OrderUtils.getClosingDirection(direction), contract.getUnifiedSymbol())
				.orElse(PositionField.newBuilder().setContract(contract).build());
		Tuple<OffsetFlagEnum, Integer> tuple = module.getModuleDescription().getClosingPolicy().resolve(operation, pos, volume);
		if(tuple.t1() == OffsetFlagEnum.OF_CloseToday) {
			PositionField updatePos = pos.toBuilder().setTdFrozen(tuple.t2()).build();
			getAccount(contract).onPosition(updatePos);
		} else if(tuple.t1() == OffsetFlagEnum.OF_CloseYesterday) {
			PositionField updatePos = pos.toBuilder().setYdFrozen(tuple.t2()).build();
			getAccount(contract).onPosition(updatePos);
		}
		return Optional.ofNullable(submitOrderReq(SubmitOrderReqField.newBuilder()
				.setOriginOrderId(id)
				.setContract(contract)
				.setGatewayId(gatewayId)
				.setDirection(direction)
				.setOffsetFlag(tuple.t1())
				.setVolume(tuple.t2())		
				.setPrice(orderPrice + factor * plusPrice)	// 自动加上超价
				.setHedgeFlag(HedgeFlagEnum.HF_Speculation)
				.setTimeCondition(priceType == PriceType.ANY_PRICE ? TimeConditionEnum.TC_IOC : TimeConditionEnum.TC_GFD)
				.setOrderPriceType(priceType == PriceType.ANY_PRICE ? OrderPriceTypeEnum.OPT_AnyPrice : OrderPriceTypeEnum.OPT_LimitPrice)
				.setVolumeCondition(VolumeConditionEnum.VC_AV)
				.setForceCloseReason(ForceCloseReasonEnum.FCR_NotForceClose)
				.setContingentCondition(ContingentConditionEnum.CC_Immediately)
				.setActionTimestamp(System.currentTimeMillis())
				.setMinVolume(1)
				.build()));
	}
	
	private String submitOrderReq(SubmitOrderReqField orderReq) {
		if(getLogger().isInfoEnabled()) {			
			getLogger().info("发单：{}，{}", orderReq.getOriginOrderId(), LocalDateTime.ofInstant(Instant.ofEpochMilli(orderReq.getActionTimestamp()), ZoneId.systemDefault()));
		}
		try {
			moduleAccount.onSubmitOrder(orderReq);
		} catch (InsufficientException e) {
			getLogger().error("发单失败。原因：{}", e.getMessage());
			tradeIntentMap.remove(orderReq.getContract().getUnifiedSymbol());
			getLogger().warn("模组余额不足，主动停用模组");
			setEnabled(false);
			return null;
		}
		try {
			if(Objects.nonNull(orderReqFilter)) {
				orderReqFilter.doFilter(orderReq);
			}
		} catch (Exception e) {
			getLogger().error("发单失败。原因：{}", e.getMessage());
			tradeIntentMap.remove(orderReq.getContract().getUnifiedSymbol());
			return null;
		}
		ContractField contract = orderReq.getContract();
		String originOrderId = module.getAccount(contract).submitOrder(orderReq);
		orderReqMap.put(originOrderId, orderReq);
		return originOrderId;
	}

	@Override
	public boolean isOrderWaitTimeout(String originOrderId, long timeout) {
		if(!orderReqMap.containsKey(originOrderId)) {
			return false;
		}
		
		SubmitOrderReqField orderReq = orderReqMap.get(originOrderId);
		return System.currentTimeMillis() - orderReq.getActionTimestamp() > timeout;
	}

	@Override
	public void cancelOrder(String originOrderId) {
		if(!orderReqMap.containsKey(originOrderId)) {
			getLogger().debug("找不到订单：{}", originOrderId);
			return;
		}
		if(!getState().isOrdering()) {
			getLogger().info("非下单状态，忽略撤单请求：{}", originOrderId);
			return;
		}
		getLogger().info("撤单：{}", originOrderId);
		ContractField contract = orderReqMap.get(originOrderId).getContract();
		Contract c = contractMgr.getContract(Identifier.of(contract.getContractId()));
		CancelOrderReqField cancelReq = CancelOrderReqField.newBuilder()
				.setGatewayId(contract.getGatewayId())
				.setOriginOrderId(originOrderId)
				.build();
		module.getAccount(c).cancelOrder(cancelReq);
	}

	@Override
	public void setEnabled(boolean enabled) {
		getLogger().info("【{}】 模组", enabled ? "启用" : "停用");
		this.enabled = enabled;
		moduleRepo.saveRuntime(getRuntimeDescription(false));
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void setOrderRequestFilter(OrderRequestFilter filter) {
		this.orderReqFilter = filter;
	}

	private boolean isReady;
	
	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public void onReady() {
		isReady = true;
	}

	@Override
	public int getDefaultVolume() {
		return module.getModuleDescription().getDefaultVolume();
	}

}
