package howto;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;
import utils.PoolUtils;

import java.util.concurrent.ExecutionException;

import static org.hyperledger.indy.sdk.ledger.Ledger.*;

public class IndyUtil {

    public static void sendNym(Pool pool, Wallet fromWallet, String did,
                        String newDid , String newKey, String role) throws IndyException, ExecutionException, InterruptedException {
        String nymRequest = buildNymRequest(did, newDid, newKey, null, role).get();
        System.out.println("NYM request JSON:\n" + nymRequest);
        String nymResponseJson = signAndSubmitRequest(pool, fromWallet, did, nymRequest).get();
        System.out.println("NYM transaction response:\n" + nymResponseJson);
    }

    public static void sendSchema(Pool pool, Wallet wallet, String submitterDid, String schemaJson) throws IndyException, ExecutionException, InterruptedException {
        String schemaRequest = buildSchemaRequest(submitterDid, schemaJson).get();
        signAndSubmitRequest(pool, wallet, submitterDid, schemaRequest).get();
    }

    public static void sendCredDef(Pool pool, Wallet wallet, String submitterDid, String credDerf) throws IndyException, ExecutionException, InterruptedException {
        String credDefRequest = buildCredDefRequest(submitterDid, credDerf).get();
        signAndSubmitRequest(pool, wallet, submitterDid, credDefRequest);
    }

    public static void sendRevocRegDef(Pool pool, Wallet wallet, String submitterDid, String revocRegDefJson) throws IndyException, ExecutionException, InterruptedException {
        String revocRegDefRequest = buildRevocRegDefRequest(submitterDid, revocRegDefJson).get();
        signAndSubmitRequest(pool, wallet, submitterDid, revocRegDefRequest).get();
    }

    public static void sendRevocRegEntry(Pool pool, Wallet wallet, String submitterDid, String revocRegId, String revocRegEntryJson) throws IndyException, ExecutionException, InterruptedException {
        String revocRegEntryRequest = buildRevocRegEntryRequest(submitterDid, revocRegId, "CL_ACCUM", revocRegEntryJson).get();
        signAndSubmitRequest(pool, wallet, submitterDid, revocRegEntryRequest);
    }

    static String getNym(Pool pool, String clientDid, String endorserDid) throws IndyException, ExecutionException, InterruptedException {
        System.out.println("\"Client\" -> Building the GET_NYM request to query Trust Anchor's Verkey as the Client");
        String getNymRequest = buildGetNymRequest(clientDid, endorserDid).get();
        System.out.println("GET_NYM request json:\n" + getNymRequest);

        System.out.println("\"Client\" -> Sending the GET_NYM request to the ledger");
        String getNymResponse = PoolUtils.ensurePreviousRequestApplied(pool, getNymRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });

        System.out.println("GET_NYM response json:\n" + getNymResponse);

        String nymFromLedger = parseGetNymResponse(getNymResponse).get();
        return nymFromLedger;
    }

    public static LedgerResults.ParseResponseResult getSchema(Pool pool, String submitterDid, String schemaId) throws IndyException, ExecutionException, InterruptedException {
        String getSchemaRequest = buildGetSchemaRequest(submitterDid, schemaId).get();
        String getSchemaResponse = PoolUtils.ensurePreviousRequestApplied(pool, getSchemaRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });
        LedgerResults.ParseResponseResult schemaInfoFromLedger = parseGetSchemaResponse(getSchemaResponse).get();
        return schemaInfoFromLedger;
    }

    public static LedgerResults.ParseResponseResult getCredDef(Pool pool, String submitterDid, String credDefId) throws IndyException, ExecutionException, InterruptedException {
        String getCredDefRequest = buildGetCredDefRequest(submitterDid, credDefId).get(); //(발행자DID, credDef Id)
        String getCredDefResponse = PoolUtils.ensurePreviousRequestApplied(pool, getCredDefRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });
        LedgerResults.ParseResponseResult credDefFromLedger = parseGetCredDefResponse(getCredDefResponse).get();
        return credDefFromLedger;
    }

    public static LedgerResults.ParseRegistryResponseResult getRevocReg(Pool pool, String submitterDid, String revocRegDefId, long timestamp) throws IndyException, ExecutionException, InterruptedException {
        String getRevocRegRequest = buildGetRevocRegRequest(submitterDid, revocRegDefId, timestamp).get();
        String getRevocRegDefResponse = PoolUtils.ensurePreviousRequestApplied(pool, getRevocRegRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });

        LedgerResults.ParseRegistryResponseResult revocRegFromLedger = parseGetRevocRegResponse(getRevocRegDefResponse).get();
        return revocRegFromLedger;
    }

    public static LedgerResults.ParseResponseResult getRevocRegDef(Pool pool, String submitterDid, String revRegDefId) throws IndyException, ExecutionException, InterruptedException {
        String getRevocRegDefRequest = buildGetRevocRegDefRequest(submitterDid, revRegDefId).get();
        String getRevocRegDefResponse = PoolUtils.ensurePreviousRequestApplied(pool, getRevocRegDefRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });

        LedgerResults.ParseResponseResult revocRegDefFromLedger = parseGetRevocRegDefResponse(getRevocRegDefResponse).get();
        return revocRegDefFromLedger;

    }

    public static LedgerResults.ParseRegistryResponseResult getRevocRegDelta(Pool pool, String submitterDid, String revocRegDefId, long from, long to) throws IndyException, ExecutionException, InterruptedException {
        String getRevocRegDeltaRequest = buildGetRevocRegDeltaRequest(submitterDid, revocRegDefId, from, to).get();
        String getRevocRegDeltaResponse = PoolUtils.ensurePreviousRequestApplied(pool, getRevocRegDeltaRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });

        LedgerResults.ParseRegistryResponseResult revocRegDeltaFromLedger = parseGetRevocRegDeltaResponse(getRevocRegDeltaResponse).get();
        return revocRegDeltaFromLedger;
    }


    public static Wallet createAndOpenWallet(String identity) throws Exception {
        System.out.println("["+identity+"]-> Create And Open wallet" + "["+identity+"]");
        String walletConfig = new JSONObject().put("id", identity+"Wallet").toString();
        String walletCredentials = new JSONObject().put("key", identity+"_wallet_key").toString();
        Wallet.createWallet(walletConfig, walletCredentials).get();
        return Wallet.openWallet(walletConfig, walletCredentials).get();
    }

    public static void onBoarding() {

    }



}
