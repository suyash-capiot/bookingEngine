/*package com.coxandkings.travel.bookingengine;

import com.coxandkings.travel.bookingengine.exception.BadSearchCriteriaException;
import com.coxandkings.travel.bookingengine.exception.ClientNotFoundException;
import com.coxandkings.travel.bookingengine.resource.MDMResources.*;
import com.coxandkings.travel.bookingengine.service.mdmclient.impl.MDMClientServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest
public class MDMClientTest extends BookingEngineApplicationTests {

    @Autowired
    private MDMClientServiceImpl mdmClientService;

    private static SearchClient searchClientReq = null;
    private static ClientRecord[] clientRecord = null;
    private static ClientStatus clientStatus = null;
    private static ClientStructure clientStructure = null;
    private static OrgHierarchy orgHierarchy = null;
    private static TransactionalCurrency[] transactionalCurrency = null;

    static {
        searchClientReq = new SearchClient();
        clientRecord = new ClientRecord[1];
        clientRecord[0] = new ClientRecord();
        clientStatus = new ClientStatus();
        clientStructure = new ClientStructure();
        orgHierarchy = new OrgHierarchy();
        transactionalCurrency = new TransactionalCurrency[3];
        transactionalCurrency[0] = new TransactionalCurrency();
        transactionalCurrency[0].set_id("59fade5ebac7f111789ec406");
        transactionalCurrency[0].setMarket("India");
        transactionalCurrency[0].setCurrency("INR");
        transactionalCurrency[0].setDefault(true);
        transactionalCurrency[1] = new TransactionalCurrency();
        transactionalCurrency[1].set_id("59fade5ebac7f111789ec405");
        transactionalCurrency[1].setMarket("India");
        transactionalCurrency[1].setCurrency("INR");
        transactionalCurrency[1].setDefault(false);
        transactionalCurrency[2] = new TransactionalCurrency();
        transactionalCurrency[2].set_id("59fae2ebbac7f111789ec407");
        transactionalCurrency[2].setMarket("India");
        transactionalCurrency[2].setCurrency("INR");
        transactionalCurrency[2].setDefault(true);
        clientRecord[0].set_id("CLIENTTYPE14");
        // clientRecord.setCreatedAt(new Date("2017-11-02T08:59:10.904Z"));
        //  clientRecord.setLastUpdated(new Date("2017-11-02T09:18:35.895Z"));
        clientRecord[0].setDeleted(false);
        clientStatus.setRemarks("");
        clientStatus.setReason("XYZ");
        //   clientStatus.setEffectiveFrom(new Date("2017-11-20T00:00:00.000Z"));
        clientStatus.setStatus("Active");
        clientRecord[0].setStatus(clientStatus);
        clientStructure.setLanguage("English");
        clientStructure.setClientCommercials(true);
        clientStructure.setClientEntityName("Micro Software");
        clientStructure.setClientMarket("India");
        clientStructure.setPointOfSale(new String[]{"Website", "White Label"});
        clientStructure.setSettlement(true);
        clientStructure.setTransactional(true);
        clientStructure.setTransactionalCurrency(transactionalCurrency);
        clientStructure.setClientEntityType("B2B");
        clientRecord[0].setClientStructure(clientStructure);
        orgHierarchy.setBU("ABCD");
        orgHierarchy.setCompany("Capiot");
        orgHierarchy.setCompanyMarket("India");
        orgHierarchy.setGroupCompany("Ezeego1 Group");
        orgHierarchy.setGroupOfCompanies("QRST");
        orgHierarchy.setMarketCurrency("INR");
        orgHierarchy.setSBU("Forex");
        clientRecord[0].setOrgHierarchy(orgHierarchy);
        clientRecord[0].set__v(0);

        searchClientReq.setData(clientRecord);
    }
    @Test
    public void testClientNotfoundById(){
        try {
            SearchClient searchClientRes = mdmClientService.getClientByIdOrEntityType("id", "5335");
        }
        catch(Exception e){
            assertEquals("MDM Client Not Found",e.getMessage());
        }
    }
    @Test
    public void testGetClientById() throws ClientNotFoundException, BadSearchCriteriaException {
        SearchClient searchClientRes = mdmClientService.getClientByIdOrEntityType("id", "CLIENTTYPE14");
        assertNotNull(searchClientRes.getData()[0].get_id());
        assertEquals(searchClientReq.getData()[0].get_id(), searchClientRes.getData()[0].get_id());
    }

    @Test
    public void testGetClientByEntityType() throws ClientNotFoundException, BadSearchCriteriaException {
        SearchClient searchClientRes = mdmClientService.getClientByIdOrEntityType("entitytype", "B2B");
        assertNotNull(searchClientRes.getData()[0].get_id());
        assertEquals(searchClientReq.getData()[0].get_id(), searchClientRes.getData()[0].get_id());
        assertEquals(searchClientReq.getData()[0].get__v(), searchClientRes.getData()[0].get__v());
        assertEquals(searchClientReq.getData()[0].getClientStructure().getClientEntityType(), searchClientRes.getData()[0].getClientStructure().getClientEntityType());
    }

    @Test
    public void testClientNotFoundByEntityType(){
        try {
            SearchClient searchClientRes = mdmClientService.getClientByIdOrEntityType("entitytype", "45");
        }
        catch(Exception e){
            assertEquals("MDM Client Not Found",e.getMessage());
        }
    }

    @Test
    public void testBadSearchCriteriaException() {
        BadSearchCriteriaException actual = null;
        SearchClient searchClientRes =null;
        try {
            searchClientRes = mdmClientService.getClientByIdOrEntityType("wrongrequest", "m2m");
        } catch (BadSearchCriteriaException e) {
            actual = e;
        }
        catch (ClientNotFoundException e) {

        }
        assertExceptionThrown(BadSearchCriteriaException.class, actual);
    }

    protected static void assertExceptionThrown(Class expected, Exception actual) {
        if (actual == null || !actual.getClass().equals(expected)) {
            fail("Exception thrown not of type " + expected.getName() + " but was " + (actual == null ? "null" : actual.getClass().getName()));
        }
    }
}
*/