package tech.quantit.northstar.main.restful;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.quantit.northstar.common.constant.ChannelType;
import tech.quantit.northstar.common.model.GatewayDescription;
import tech.quantit.northstar.common.model.ResultBean;
import tech.quantit.northstar.common.utils.MarketDataLoadingUtils;
import tech.quantit.northstar.data.IGatewayRepository;
import tech.quantit.northstar.gateway.api.IContractManager;
import tech.quantit.northstar.gateway.api.domain.contract.Contract;
import tech.quantit.northstar.gateway.api.utils.MarketDataRepoFactory;
import xyz.redtorch.pb.CoreField.BarField;

@RequestMapping("/northstar/data")
@RestController
public class GatewayDataController {

	@Autowired
	private MarketDataRepoFactory mdRepoFactory;
	
	@Autowired
	private IGatewayRepository gatewayRepo;
	
	@Autowired
	private IContractManager contractMgr;
	
	private MarketDataLoadingUtils utils = new MarketDataLoadingUtils();
	
	@GetMapping("/bar/min")
	public ResultBean<List<byte[]>> loadWeeklyBarData(String gatewayId, String unifiedSymbol, long refStartTimestamp, boolean firstLoad){
		Assert.notNull(unifiedSymbol, "合约代码不能为空");
		GatewayDescription gd = gatewayRepo.findById(gatewayId);
		if(gd.getChannelType() == ChannelType.PLAYBACK || gd.getChannelType() == ChannelType.SIM) {
			return new ResultBean<>(Collections.emptyList());
		}
		Contract contract = contractMgr.getContract(gatewayId, unifiedSymbol);
		LocalDate start = utils.getFridayOfLastWeek(refStartTimestamp);
		if(firstLoad && Period.between(start, LocalDate.now()).getDays() < 7) {
			start = start.minusWeeks(1);
		}
		LocalDate end = utils.getCurrentTradeDay(refStartTimestamp, firstLoad);
		List<BarField> result = Collections.emptyList();
		for(int i=0; i<3; i++) {
			result = mdRepoFactory.getInstance(gatewayId).loadBars(contract.contractField().getUnifiedSymbol(), start.minusWeeks(i), end.minusWeeks(i));
			if(!result.isEmpty()) {
				break;
			}
		}
		
		return new ResultBean<>(result.stream().map(BarField::toByteArray).toList());
	}
	
}
