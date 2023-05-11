// ModuleMgmt.spec.js created with Cypress
//
// Start writing your Cypress tests below!
// If you're unfamiliar with how Cypress works,
// check out the link below and learn how to write your first test:
// https://on.cypress.io/writing-first-test
/* eslint-disable */

describe('模组管理-测试', () => {
    before(() => {
        cy.visit('https://localhost')
        cy.contains('用户名').parent().find('input').type('admin')
        cy.contains('密码').parent().find('input').type('123456')
        cy.contains('登录').click()
        cy.wait(500)
        cy.contains('新建').click()
        cy.get('.el-dialog').contains('网关类型').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').contains('SIM').click()
        cy.get('.el-dialog').contains('订阅合约').parent().find('.el-select').type('模拟合约')
        cy.get('.el-select-dropdown').contains('模拟合约').click()
        cy.get('.el-dialog').filter(':visible').find('button').last().click()
        cy.visit('https://localhost/#/tdgateway')
        cy.wait(300)
        cy.get('button').contains('新建').click()
        cy.get('.el-dialog').contains('账户ID').parent().find('input').type('testAccount')
        cy.get('.el-dialog').contains('账户类型').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').filter(':visible').contains('SIM').click()
        cy.get('.el-dialog').contains('行情网关').parent().find('.el-select').click()
        cy.get('#bindedGatewayOption_SIM').click()
        cy.get('.el-dialog').filter(':visible').find('button').last().click()
        cy.get('.el-table__row').first().contains('连线').click()
        cy.wait(1000)
        cy.get('.el-table__row').first().contains('出入金').click()
        cy.wait(500)
        cy.get('.el-dialog').filter(':visible').find('input').type(50000)
        cy.get('.el-dialog').filter(':visible').find('button').contains('出入金').click()
        cy.wait(1000)
        cy.get('.el-dialog').filter(':visible').find('button').first().click()
        cy.visit('https://localhost/#/mktgateway')
        cy.wait(500)
        cy.get('.el-table__row').first().contains('连线').click()
        cy.wait(1000)
        cy.visit('https://localhost/#/module')
        cy.wait(300)
    })

    describe('模组管理-基础测试', () => {
        beforeEach(() => {
            cy.Cookies.preserveOnce('JSESSIONID')
        })

        it('应该可以创建模组', () => {
            cy.intercept('POST','/northstar/module').as('createModule')
            cy.contains('新建').click()
            cy.get('.el-dialog').contains('模组名称').parent().find('input').type('TESTM')
            cy.get('.el-dialog').contains('交易策略').click()
            cy.get('#showDemoStrategy').click()
            cy.get('.el-dialog').contains('绑定策略').parent().find('input').click()
            cy.get('.el-select-dropdown').contains('示例-简单策略').click()
            cy.get('.el-dialog').contains('操作间隔').parent().find('input').type(600)
            cy.get('.el-dialog').contains('账户绑定').click()
            cy.get('.el-dialog').contains('绑定账号').parent().find('input').click()
            cy.get('.el-select-dropdown').contains('testAccount').click()
            cy.get('.el-dialog').contains('模组分配金额').parent().find('input').clear().type('20000')
            cy.get('.el-dialog').filter(':visible').find('.el-select').filter(':visible').last().click()
            cy.get('.el-select-dropdown').contains('模拟合约').click()
            cy.get('.el-dialog').filter(':visible').click()
            cy.get('#saveModuleSettings').click()
            cy.wait('@createModule').should('have.nested.property', 'response.statusCode', 200)
            cy.wait(1000)

            cy.get('.el-table__row').filter(':visible').should('have.length', 1)
        })

        it('应该可以修改模组', () => {
            cy.intercept('PUT','/northstar/module?reset=false').as('updateModule')
            cy.get('.el-table__row').contains('修改').click()
            cy.get('.el-dialog').contains('账户绑定').click()
            cy.get('.el-dialog').contains('模组分配金额').parent().find('input').clear().type('40000')
            cy.get('#saveModuleSettings').click()
            cy.wait('@updateModule').should('have.nested.property', 'response.statusCode', 200)
            cy.wait(1000)
        })

        it('应该可以重置模组', () => {
            cy.intercept('PUT','/northstar/module?reset=true').as('resetModule')
            cy.get('.el-table__row').contains('修改').click()
            cy.wait(500)
            cy.get('#resetModuleSettings').click()
            cy.get('.el-popconfirm').filter(':visible').find('button').contains('确定').click()
            cy.wait('@resetModule').should('have.nested.property', 'response.statusCode', 200)
        })

        it('应该可以启用、停用模组，模组启用时删除按钮不可用，停用时删除按钮可用', () => {
            cy.get('.el-table__row').contains('已停用')
            cy.get('.el-table__row').contains('删除').should('be.enabled')
            cy.get('.el-table__row').contains('启用').click()
            cy.get('.el-table__row').contains('运行中')
            cy.get('.el-table__row').contains('停用').click()
            cy.get('.el-table__row').contains('删除').should('be.enabled')
        })

        it('当账户网关被模组绑定后，尝试删除账户会报错', () => {
            cy.visit('https://localhost/#/tdgateway')
            cy.wait(300)
            cy.intercept('DELETE','/northstar/gateway?gatewayId=testAccount').as('delGateway')
            cy.get('.el-table__row').first().contains('断开').click()
            cy.get('.el-table__row').first().contains('删除').click()
            cy.get('.el-popconfirm').filter(':visible').find('button').contains('确定').click()
            cy.wait('@delGateway').should('have.nested.property', 'response.statusCode', 500)
            cy.get('.el-message--error').contains('先解除绑定')

            cy.get('.el-table__row').should('have.length', 1)
        })

        it('应该可以删除模组', () => {
            cy.visit('https://localhost/#/module')
            cy.wait(300)
            cy.intercept('DELETE','/northstar/module?name=TESTM').as('removeModule')
            cy.get('.el-table__row').first().contains('删除').click()
            cy.get('.el-popconfirm').filter(':visible').find('button').contains('确定').click()
            cy.wait('@removeModule').should('have.nested.property', 'response.statusCode', 200)

            cy.get('.el-table__row').should('have.length', 0)
        })
    })

    describe('模组状态管理-测试', () => {
        before(()=>{
            cy.visit('https://localhost/#/module')
            cy.contains('新建').click()
            cy.get('.el-dialog').contains('模组名称').parent().find('input').type('TESTM')
            cy.get('.el-dialog').contains('交易策略').click()
            cy.get('#showDemoStrategy').click()
            cy.get('.el-dialog').contains('绑定策略').parent().find('input').click()
            cy.get('.el-select-dropdown').contains('示例-简单策略').click()
            cy.get('.el-dialog').contains('操作间隔').parent().find('input').type(60)
            cy.get('.el-dialog').contains('账户绑定').click()
            cy.get('.el-dialog').contains('绑定账号').parent().find('input').click()
            cy.get('.el-select-dropdown').contains('testAccount').click()
            cy.get('.el-dialog').contains('模组分配金额').parent().find('input').clear().type('20000')
            cy.get('.el-dialog').filter(':visible').find('.el-select').filter(':visible').last().click()
            cy.get('.el-select-dropdown').contains('模拟合约').click()
            cy.get('.el-dialog').contains('模组分配金额').parent().find('input').click()
            cy.get('#saveModuleSettings').click()
        })

        beforeEach(() => {
            cy.Cookies.preserveOnce('JSESSIONID')
        })

        it('模组运行状态能正常查询，没有报错', () => {
            cy.intercept('/northstar/module/rt/info*').as('getRtInfo')
            cy.intercept('/northstar/module/deal/record*').as('getRecord')
            cy.get('.el-table__row').contains('运行状态').click()
            cy.wait('@getRtInfo').should('have.nested.property', 'response.statusCode', 200)
            cy.wait('@getRecord').should('have.nested.property', 'response.statusCode', 200)
        })

        it('可以手工增加模组持仓', () => {
            cy.get('#editPosition').click()
            cy.get('.el-dialog').contains('合约代码').parent().click()
            cy.get('.el-select-dropdown').filter(':visible').contains('模拟合约').click()
            cy.get('.el-dialog').contains('成交方向').parent().click()
            cy.wait(300)
            cy.get('.el-select-dropdown').filter(':visible').contains('多开').click()
            cy.get('.el-dialog').contains('成交价').parent().find('input').type(3000)
            cy.get('#editPositionVol').find('input').type(1)
            cy.get('#savePosition').click()

            cy.get('#modulePositionTbl').find('.el-table__row').should('have.length', 1)
            cy.get('#modulePositionTbl').find('.el-table__row').find('.cell').eq(2).should('have.text', '1')

            cy.get('#editPosition').click()
            cy.get('.el-dialog').contains('合约代码').parent().click()
            cy.get('.el-select-dropdown').filter(':visible').contains('模拟合约').click()
            cy.get('.el-dialog').contains('成交方向').parent().click()
            cy.wait(300)
            cy.get('.el-select-dropdown').filter(':visible').contains('多开').click()
            cy.get('.el-dialog').contains('成交价').parent().find('input').type(3000)
            cy.get('#editPositionVol').find('input').type(1)
            cy.get('#savePosition').click()
            cy.wait(300)
            cy.get('#modulePositionTbl').find('.el-table__row').should('have.length', 1)
            cy.get('#modulePositionTbl').find('.el-table__row').find('.cell').eq(2).should('have.text', '2')
        })

        it('可以手工减少模组持仓', () => {
            cy.get('#editPosition').click()
            cy.get('.el-dialog').contains('合约代码').parent().click()
            cy.get('.el-select-dropdown').filter(':visible').contains('模拟合约').click()
            cy.get('.el-dialog').contains('成交方向').parent().click()
            cy.wait(300)
            cy.get('.el-select-dropdown').contains('空平').click()
            cy.get('.el-dialog').contains('成交价').parent().find('input').type(3000)
            cy.get('#editPositionVol').find('input').type(1)
            cy.get('#savePosition').click()
            cy.wait(300)
            cy.get('#modulePositionTbl').find('.el-table__row').should('have.length', 1)
            cy.get('#modulePositionTbl').find('.el-table__row').find('.cell').eq(2).should('have.text', '1')

            cy.get('#editPosition').click()
            cy.get('.el-dialog').contains('合约代码').parent().click()
            cy.get('.el-select-dropdown').filter(':visible').contains('模拟合约').click()
            cy.get('.el-dialog').contains('成交方向').parent().click()
            cy.wait(300)
            cy.get('.el-select-dropdown').contains('空平').click()
            cy.get('.el-dialog').contains('成交价').parent().find('input').type(3000)
            cy.get('#editPositionVol').find('input').type(1)
            cy.get('#savePosition').click()
            cy.wait(500)

            cy.get('#modulePositionTbl').find('.el-table__row').should('have.length', 0)
        })

        after(() => {
            cy.request('DELETE', 'https://localhost/northstar/module?name=TESTM')
        })
    })

    after(() => {
        cy.request('DELETE', 'https://localhost/northstar/gateway/connection?gatewayId=testAccount')
        cy.request('DELETE', 'https://localhost/northstar/gateway/connection?gatewayId=SIM')
        cy.request('DELETE', 'https://localhost/northstar/gateway?gatewayId=testAccount')
        cy.request('DELETE', 'https://localhost/northstar/gateway?gatewayId=SIM')
    })
})

describe('模组创建-测试', () => {
    before(() => {
        cy.visit('https://localhost')
        cy.contains('用户名').parent().find('input').type('admin')
        cy.contains('密码').parent().find('input').type('123456')
        cy.contains('登录').click()
        cy.wait(500)
    })

    beforeEach(() => {
        cy.Cookies.preserveOnce('JSESSIONID')
    })
    
    it('可以创建回测模组', () => {
        cy.get('#tabMarketData').click()
        cy.contains('新建').click()
        cy.get('.el-dialog').contains('网关类型').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').contains('PLAYBACK').click()
        cy.get('.el-dialog').contains('网关ID').parent().find('.el-input').type('行情回放')
        cy.get('.el-dialog').contains('订阅合约').parent().find('.el-select').type('螺纹钢指数')
        cy.get('.el-select-dropdown').contains('螺纹钢指数').click()
        cy.get('.el-dialog').filter(':visible').find('button').contains('网关配置').click()
        cy.get('.el-dialog').filter(':visible').contains('回放日期').parent().find('input').first().click()
        cy.get('.el-picker-panel__sidebar').contains('最近一个月').click()
        cy.get('.el-dialog').filter(':visible').contains('回放精度').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').filter(':visible').contains('低').click()
        cy.get('.el-dialog').filter(':visible').contains('回放速度').parent().find('.el-select').click()
        cy.wait(300)
        cy.get('.el-select-dropdown').filter(':visible').contains('正常').click()
        cy.get('.el-dialog').filter(':visible').contains("保 存").click()
        cy.wait(300)
        cy.get('.el-dialog').filter(':visible').contains("保 存").click()
        cy.wait(300)
        cy.get('#tabAccount').click()
        cy.wait(300)
        cy.get('button').contains('新建').click()
        cy.get('.el-dialog').contains('账户ID').parent().find('input').type('pbAccount')
        cy.get('.el-dialog').contains('账户类型').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').filter(':visible').contains('SIM').click()
        cy.get('.el-dialog').contains('行情网关').parent().find('.el-select').click()
        cy.get('#bindedGatewayOption_行情回放').click()
        cy.get('.el-dialog').filter(':visible').find('button').last().click()
        cy.wait(300)
        cy.get('#tabModule').click()
        cy.intercept('POST','/northstar/module').as('createModule')
        cy.wait(300)
        cy.get('#createModule').click()
        cy.get('.el-dialog').contains('模组名称').parent().find('input').type('PLAYBACKM')
        cy.get('.el-dialog').contains('模组用途').parent().find('input').click()
        cy.get('.el-select-dropdown').contains('回测').click()
        cy.get('.el-dialog').contains('交易策略').click()
        cy.get('#showDemoStrategy').click()
        cy.get('.el-dialog').contains('绑定策略').parent().find('input').click()
        cy.get('.el-select-dropdown').contains('示例-简单策略').click()
        cy.get('.el-dialog').contains('操作间隔').parent().find('input').type(600)
        cy.get('.el-dialog').contains('账户绑定').click()
        cy.get('.el-dialog').contains('绑定账号').parent().find('input').click()
        cy.get('.el-select-dropdown').contains('pbAccount').click()
        cy.wait(300)
        cy.get('.el-dialog').filter(':visible').find('.el-select').filter(':visible').last().click()
        cy.get('.el-select-dropdown').contains('螺纹钢指数').click()
        cy.get('.el-dialog').contains('模组分配金额').parent().find('input').clear().type('20000')
        cy.get('.el-dialog').filter(':visible').click()
        cy.get('#saveModuleSettings').click()
        cy.wait('@createModule').should('have.nested.property', 'response.statusCode', 200)
    })

    after(() => {
        cy.request('DELETE', 'https://localhost/northstar/module?name=PLAYBACKM')
        cy.request('DELETE', 'https://localhost/northstar/gateway?gatewayId=pbAccount')
        cy.request('DELETE', 'https://localhost/northstar/gateway?gatewayId=行情回放')
    })
})