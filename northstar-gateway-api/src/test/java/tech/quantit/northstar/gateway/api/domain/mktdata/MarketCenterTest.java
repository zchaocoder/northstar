package tech.quantit.northstar.gateway.api.domain.mktdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.quantit.northstar.common.event.FastEventEngine;
import tech.quantit.northstar.common.model.Identifier;
import tech.quantit.northstar.gateway.api.MarketGateway;
import tech.quantit.northstar.gateway.api.domain.contract.ContractDefinition;
import tech.quantit.northstar.gateway.api.domain.contract.ContractDefinition.Type;
import tech.quantit.northstar.gateway.api.domain.contract.Instrument;
import tech.quantit.northstar.gateway.api.domain.time.IPeriodHelperFactory;
import tech.quantit.northstar.gateway.api.domain.time.PeriodHelper;
import test.common.TestFieldFactory;
import xyz.redtorch.pb.CoreEnum.ExchangeEnum;
import xyz.redtorch.pb.CoreEnum.ProductClassEnum;
import xyz.redtorch.pb.CoreField.ContractField;
import xyz.redtorch.pb.CoreField.GatewaySettingField;
import xyz.redtorch.pb.CoreField.TickField;

class MarketCenterTest {

	String GATEWAY_ID = "testGateway";
	TestFieldFactory factory = new TestFieldFactory(GATEWAY_ID);
	
	IPeriodHelperFactory phFactory = mock(IPeriodHelperFactory.class);
	PeriodHelper pHelper = mock(PeriodHelper.class);
	MarketCenter center;
	
	Instrument ins1 = new Instrument() {
		
		@Override
		public ProductClassEnum productClass() {
			return ProductClassEnum.FUTURES;
		}
		
		@Override
		public String name() {
			return "螺纹2305";
		}
		
		@Override
		public Identifier identifier() {
			return new Identifier("rb2305@SHFE@FUTURES");
		}
		
		@Override
		public ExchangeEnum exchange() {
			return ExchangeEnum.SHFE;
		}
		
		@Override
		public ContractField mergeToContractField(ContractDefinition contractDef) {
			return ContractField.newBuilder()
					.setSymbol(identifier().value().replaceAll("(.+)@.+", "$1"))
					.setUnifiedSymbol(identifier().value())
					.setExchange(ExchangeEnum.SHFE)
					.setProductClass(ProductClassEnum.FUTURES)
					.setContractId(identifier().value())
					.setGatewayId(GATEWAY_ID)
					.setName(name())
					.build();
		}
	};
	
	Instrument ins2 = new Instrument() {
		
		@Override
		public ProductClassEnum productClass() {
			return ProductClassEnum.FUTURES;
		}
		
		@Override
		public String name() {
			return "螺纹2310";
		}
		
		@Override
		public Identifier identifier() {
			return new Identifier("rb2310@SHFE@FUTURES");
		}
		
		@Override
		public ExchangeEnum exchange() {
			return ExchangeEnum.SHFE;
		}
		
		@Override
		public ContractField mergeToContractField(ContractDefinition contractDef) {
			return ContractField.newBuilder()
					.setSymbol(identifier().value().replaceAll("(.+)@.+", "$1"))
					.setUnifiedSymbol(identifier().value())
					.setExchange(ExchangeEnum.SHFE)
					.setProductClass(ProductClassEnum.FUTURES)
					.setContractId(identifier().value())
					.setGatewayId(GATEWAY_ID)
					.setName(name())
					.build();
		}
	};
	
	Instrument ins3 = new Instrument() {
		
		@Override
		public ProductClassEnum productClass() {
			return ProductClassEnum.OPTION;
		}
		
		@Override
		public String name() {
			return "rb2305C5000";
		}
		
		@Override
		public Identifier identifier() {
			return new Identifier("rb2305C5000@SHFE@OPTIONS");
		}
		
		@Override
		public ExchangeEnum exchange() {
			return ExchangeEnum.SHFE;
		}
		
		@Override
		public ContractField mergeToContractField(ContractDefinition contractDef) {
			return ContractField.newBuilder()
					.setSymbol(identifier().value().replaceAll("(.+)@.+", "$1"))
					.setUnifiedSymbol(identifier().value())
					.setExchange(ExchangeEnum.SHFE)
					.setProductClass(ProductClassEnum.OPTION)
					.setContractId(identifier().value())
					.setUnderlyingSymbol("rb2305")
					.setGatewayId(GATEWAY_ID)
					.setName(name())
					.build();
		}
	};
	
	@BeforeEach
	void prepare() {
		ContractDefinition def1 = ContractDefinition.builder()
				.name("RB指数")
				.symbolPattern(Pattern.compile("rb\\d+@.+@FUTURES"))
				.productClass(ProductClassEnum.FUTURES)
				.exchange(ExchangeEnum.SHFE)
				.type(Type.INDEX)
				.build();
		ContractDefinition def2 = ContractDefinition.builder()
				.name("RB期权链")
				.symbolPattern(Pattern.compile("rb.+@.+@OPTIONS"))
				.productClass(ProductClassEnum.OPTION)
				.exchange(ExchangeEnum.SHFE)
				.type(Type.OTHERS)
				.build();
		ContractDefinition def3 = ContractDefinition.builder()
				.name("全部合约")
				.symbolPattern(Pattern.compile(".+"))
				.productClass(ProductClassEnum.UNRECOGNIZED)
				.exchange(ExchangeEnum.UNRECOGNIZED)
				.type(Type.OTHERS)
				.build();
		center = new MarketCenter(List.of(def1, def2, def3), mock(FastEventEngine.class));
		
		when(phFactory.newInstance(anyInt(), anyBoolean(), any(ContractField.class))).thenReturn(pHelper);
		when(pHelper.getRunningBaseTimeFrame()).thenReturn(List.of(LocalTime.now().plusMinutes(1).withSecond(0).withNano(0)));
	}
	
	@Test
	void testAddInstument() {
		MarketGateway gateway = mock(MarketGateway.class);
		when(gateway.getGatewaySetting()).thenReturn(GatewaySettingField.newBuilder().setGatewayId(GATEWAY_ID).build());
		center.addInstrument(ins1, gateway, phFactory);
		center.addInstrument(ins2, gateway, phFactory);
		center.addInstrument(ins3, gateway, phFactory);
		
		assertThat(center.getContracts(GATEWAY_ID)).hasSize(3);
		assertThat(center.getContracts("")).hasSize(3);
	}

	@Test
	void testAggregateContract() {
		MarketGateway gateway = mock(MarketGateway.class);
		when(gateway.getGatewaySetting()).thenReturn(GatewaySettingField.newBuilder().setGatewayId(GATEWAY_ID).build());
		center.addInstrument(ins1, gateway, phFactory);
		center.addInstrument(ins2, gateway, phFactory);
		center.addInstrument(ins3, gateway, phFactory);
		center.loadContractGroup(GATEWAY_ID);
		
		assertThat(center.getContracts(GATEWAY_ID)).hasSize(6);
		assertThat(center.getContracts("")).hasSize(6);
	}
	
	@Test 
	void testTick() {
		MarketGateway gateway = mock(MarketGateway.class);
		when(gateway.getGatewaySetting()).thenReturn(GatewaySettingField.newBuilder().setGatewayId(GATEWAY_ID).build());
		center.addInstrument(ins1, gateway, phFactory);
		center.addInstrument(ins2, gateway, phFactory);
		center.addInstrument(ins3, gateway, phFactory);
		center.loadContractGroup(GATEWAY_ID);
		
		TickField t = factory.makeTickField("rb2305", 5000);
		assertDoesNotThrow(() -> {
			center.onTick(t);
		});
	}
	
}
