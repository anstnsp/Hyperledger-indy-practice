package howto;

import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.Ledger;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.PoolUtils;

import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.*;
import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class CreateSchemaAndCredDef {

    public static void run() throws Exception {
        System.out.println("\n################## howto.CreateSchemaAndCredDef -> started ##################\n");

        String poolName = "pool";
        String stewardSeed = "000000000000000000000000Steward1";
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        /**
         * 스텝1.
         * 1)인디원장 연결
         * 2)지갑생성
         * 3)DID, 인증키 생성 및 저장
         * 4)원장에 goverment를 TRUST_ANCHOR로 등록
         */
        System.out.println("\n1. Creating a new local pool ledger configuration that can be used later to connect pool nodes.\n");
        PoolUtils.createPoolLedgerConfig(poolName);

        System.out.println("\n2. Open pool ledger and get the pool handle from libindy.\n");
        Pool pool = Pool.openPoolLedger(poolName, "{}").get();

        System.out.println("\n3-1.\"Steward\" -> Creates a new secure stewardWallet\n");
        String stewardWalletConfig = new JSONObject().put("id", "stewardWallet").toString();
        String stewardWalletCredentials = new JSONObject().put("key", "steward_wallet_key").toString();
        Wallet.createWallet(stewardWalletConfig, stewardWalletCredentials).get();
        Wallet stewardWallet = Wallet.openWallet(stewardWalletConfig, stewardWalletCredentials).get();

        System.out.println("\n3-2.\"Goverment\" -> Creates a new secure govermentWallet\n");
        String govermentWalletConfig = new JSONObject().put("id", "govermentWallet").toString();
        String govermentWalletCredentials = new JSONObject().put("key", "goverment_wallet_key").toString();
        Wallet.createWallet(govermentWalletConfig, govermentWalletCredentials).get();
        System.out.println("\n4. Open wallet and get the wallet handle from libindy\n");
        Wallet govermentWallet = Wallet.openWallet(govermentWalletConfig, govermentWalletCredentials).get();

        System.out.println("\n5.\"Steward\" -> Generating and storing steward DID and Verkey\n");
        String did_json = "{\"seed\": \"" + stewardSeed + "\"}";
        DidResults.CreateAndStoreMyDidResult stewardResult = Did.createAndStoreMyDid(stewardWallet, did_json).get();
        String defaultStewardDid = stewardResult.getDid();
        System.out.println("Steward DID: " + defaultStewardDid);
        System.out.println("Steward Verkey: " + stewardResult.getVerkey());

        System.out.println("\n6.\"Goverment\" -> Generating and storing Trust Anchor DID and Verkey\n");
        DidResults.CreateAndStoreMyDidResult govermentResult = Did.createAndStoreMyDid(govermentWallet, "{}").get();
        String govermentDid = govermentResult.getDid();
        String govermentVerkey = govermentResult.getVerkey();
        System.out.println("Trust anchor DID: " + govermentDid);
        System.out.println("Trust anchor Verkey: " + govermentVerkey);

        System.out.println("\n7.\"Steward\" -> Build NYM request to add Trust Anchor to the ledger\n");
        String nymRequest = buildNymRequest(defaultStewardDid, govermentDid, govermentVerkey, null, "TRUST_ANCHOR").get();
        System.out.println("NYM request JSON:\n" + nymRequest);

        System.out.println("\n8.\"Steward\" -> Sending the nym request to ledger\n");
        String nymResponseJson = signAndSubmitRequest(pool, stewardWallet, defaultStewardDid, nymRequest).get();
        System.out.println("NYM transaction response:\n" + nymResponseJson);

        /**
         * 스텝2.
         * 1)스키마 생성
         * 2)원장에 스키마 등록
         * 스키마란 - 증명서에 들어갈 속성 항목들을 정의한 JSON 데이터
         */
        System.out.println("\n9.\"Goverment\" -> Build the SCHEMA request to add new schema to the ledger as a Trust Anchor\n");
        String schemaName = "gvt";
        String schemaVersion = "1.0";
        String schemaAttributes = new JSONArray().put("name").put("age").put("sex").put("height").toString();
        System.out.println("schemaAttributes : " + schemaAttributes);

        AnoncredsResults.IssuerCreateSchemaResult createSchemaResult =
                issuerCreateSchema(govermentDid, schemaName, schemaVersion, schemaAttributes).get();
        String schemaId = createSchemaResult.getSchemaId();
        String schemaJson = createSchemaResult.getSchemaJson();
        System.out.println("Schema ID : " + schemaId);
        System.out.println("Schema: " + schemaJson);
        String schemaRequest = buildSchemaRequest(govermentDid, schemaJson).get();
        System.out.println("Schema request:\n" + schemaRequest);

        System.out.println("\n10. \"Goverment\" -> Sending the SCHEMA request to the ledger\n");
        String schemaResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, schemaRequest).get();
        System.out.println("Schema response:\n" + schemaResponse);


        /**
         * 스텝3.
         * 1)원장에서 스키마 가져옴.
         * 2)Credential definition 생성 및 지갑에 저장
         * 3)원장에 Credential definition 등록
         * Credential definition - 신원증명에 어떤 Schema가 사용됐는지, 어떤 용도로 사용될 수 있는지 추측할 수 있는 tag,
         *                         어떤 서명 기법을 사용하여 신원증명을 발행할지 나타내는 type,
         *                         그리고 신원증명 폐기 관련 정보가 포함된 config항목이 등이 있다.
         */

        System.out.println("\n11. \"Goverment\" -> get Schema from Ledger\n");
        String getSchemaRequest = buildGetSchemaRequest(govermentDid,schemaId).get();
        String getSchemaResponse = submitRequest(pool, getSchemaRequest).get();
//        String getSchemaResponse = PoolUtils.ensurePreviousRequestApplied(pool, getSchemaRequest, response -> {
//            JSONObject getSchemaResponseObject = new JSONObject(response);
//            return ! getSchemaResponseObject.getJSONObject("result").isNull("seqNo");
//        });

        // !!IMPORTANT!!
        // It is important to get Schema from Ledger and parse it to get the correct schema JSON and correspondent id in Ledger
        // After that we can create CredentialDefinition for received Schema(not for result of indy_issuer_create_schema)
        System.out.println("\n12. \"Goverment\" -> parsing GetSchemaResponse from Ledger\n");
        LedgerResults.ParseResponseResult schemaInfoFromLedger = parseGetSchemaResponse(getSchemaResponse).get();
        String schemaJsonFromLedger = schemaInfoFromLedger.getObjectJson();
        System.out.println("schemaJsonFromLedger : " + schemaJsonFromLedger);

        System.out.println("\n13.  \"Goverment\" -> Creating and storing CRED DEF using anoncreds as Trust Anchor, for the given Schema\n");
        String credDefTag = "test";
        String credDefConfigJson = new JSONObject().put("support_revocation", false).toString();
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createCredDefResult =
                issuerCreateAndStoreCredentialDef(govermentWallet, govermentDid, schemaJsonFromLedger, credDefTag, null, credDefConfigJson).get();
        String credDefId = createCredDefResult.getCredDefId();
        String credDefJson = createCredDefResult.getCredDefJson();
        System.out.println("Returned Cred Definition ID:\n" + credDefId);
        System.out.println("Returned Cred Definition:\n" + credDefJson);

        System.out.println("\n14.\"Goverment\" -> Build CRED DEF request to add new Credential Definition to the ledger as a Trust Anchor \n");
        String credDefRequest = Ledger.buildCredDefRequest(govermentDid, credDefJson).get();
        System.out.println("credDefRequest : \n" + credDefRequest);

        System.out.println("\n15. \"Goverment\" -> Send  \"Goverment gvt\" Credential Definition to Ledger");
        String credDefResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, credDefRequest).get();
        System.out.println("credDefResponse : " + credDefResponse);

        /**
         * 스텝4. clean up code
         * 13)만든 지갑들 닫고 삭제.
         * 14)노드풀 닫음
         * 15)원장설정 삭제
         */
        System.out.println("\n16. Close and delete wallet\n");
        stewardWallet.closeWallet().get();
        Wallet.deleteWallet(stewardWalletConfig, stewardWalletCredentials).get();
        govermentWallet.closeWallet().get();
        Wallet.deleteWallet(govermentWalletConfig, govermentWalletCredentials).get();

        System.out.println("\n17. Close pool\n");
        pool.closePoolLedger().get();

        System.out.println("\n18. Delete pool ledger config\n");
        Pool.deletePoolLedgerConfig(poolName).get();

        System.out.println("\n################## howto.CreateSchemaAndCredDef -> completed ##################\n");
    }


}
