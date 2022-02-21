package howto;

import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.PoolUtils;

import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.*;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.proverStoreCredential;
import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static org.hyperledger.indy.sdk.ledger.Ledger.signAndSubmitRequest;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class IssueCredential {

    public static void run() throws Exception {
        System.out.println("\n################## howto.IssueCredential -> started ##################\n");

        String poolName = "pool";
        String stewardSeed = "000000000000000000000000Steward1";
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        /**
         * 스텝1.** 공통로직 **
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
        String schemaName = "IDcard";
        String schemaVersion = "1.0";
        String schemaAttributes = new JSONArray().put("name").put("age").put("sex").put("height").toString();
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

        System.out.println("\n14. \"Goverment\" -> Send  \"Goverment gvt\" Credential Definition to Ledger");
        String credDefRequest = buildCredDefRequest(govermentDid, credDefJson).get();
        String credDefResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, credDefRequest).get();
        System.out.println("credDefResponse : " + credDefResponse);

        /**
         * 스텝4.
         * 0.[사용자(holder)] -> 사용자는 발행자에게 Credential offer 달라고 요청.
         * 1.[발행인(issuer)] -> 발행인은 Credential offer를 생성하여 사용자(holder)에게 전달.
         * 2.[사용자(holder)] -> 발행인으로부터 받은 VC에 대한 소유권증명을 위한 Link secret 생성.
         * 3.[사용자(holder)] -> 발행인에게 받은 Credential offer를 이용해 발행인의 Credential definition을 요청하는 트랜잭션(buildGetCredDefRequest) 생성한 후
         *                             블록체인에 전송하여 발행인의 Credential definition 획득.
         * 4.[사용자(holder)] -> VC생성요청에 필요한 정보를 모두 획득한 사용자는 prover_create_credential_request를 통해 주어진 Credential offer에 대한 VC발급요청 데이터를 생성하여 발행인 에게 전송.
         * 5.[발행인(issuer)] -> 사용자로 부터 VC요청 받은 발행인은 issuer_create_credential API를 통해 VC를 생성한 후 사용자에게 전달.
         * 6.[사용자(holder)] -> VC를 수신한 사용자는 prover_store_credential API를 이용해 지갑에 VC를 저장함으로써 VC 생성 및 발급 과정 끝.
         *
         * [발행인(issuer)]
         * 1.발행인은 Credential offer를 생성하여 사용자(holder)에게 전달.
         * (credential offer에는 사용자가 블록체인에 저장된 발행인의 VC양식을 가져오기 위해 필요한 Schema id, Credential definition id가 포함되어 있다.)
         * 5.사용자로 부터 VC 요청 받은 발행인은 issuer_create_credential API를 통해 VC를 생성한 후 사용자에게 전달.
         *
         * [사용자(holder)]
         * 0.사용자는 발행자에게 Credential offer 달라고 요청.
         * 2.발행인으로부터 받은 VC에 대한 소유권증명을 위한 Link secret 생성.
         * 3.발행인에게받은 Credential offer를 이용해 발행인의 Credential definition을 요청하는 트랜잭션(buildGetCredDefRequest) 생성한 후
         *   블록체인에 전송하여 발행인의 Credential definition 획득.
         * 4.VC생성 요청에 필요한 정보를 모두 획득한 사용자는 prover_create_credential_request를 통해 주어진 Credential offer에 대한 VC발급 요청 데이터를 생성하여 발행인 에게 전송.
         * (VC 발급요청 데이터에는 발급받길 원하는 VC의 Credential definition과 VC 소유권 증명을위한 Link secret등 포함.
         * 6.VC를 수신한 사용자는 prover_store_credential API를 이용해 지갑에 VC를 저장함으로써 VC 생성 및 발급 과정끝.
         */
        System.out.println("\n15. \"Prover\" -> Creating Prover wallet and opening it to get the handle\n");
        String proverWalletConfig = new JSONObject().put("id", "proverWallet").toString();
        String proverWalletCredentials = new JSONObject().put("key", "prover_wallet_key").toString();
        Wallet.createWallet(proverWalletConfig, proverWalletCredentials).get();
        Wallet proverWallet = Wallet.openWallet(proverWalletConfig, proverWalletCredentials).get();

        DidResults.CreateAndStoreMyDidResult proverResult = Did.createAndStoreMyDid(proverWallet, "{}").get();
        String proverDID = proverResult.getDid();
        String proverVerkey = proverResult.getVerkey();
        System.out.println("Trust anchor(prover) DID: " + proverDID);
        System.out.println("Trust anchor(prover) Verkey: " + proverVerkey);

        System.out.println("===== 사용자(holder, prover)가 Credential offer 달라고 요청 했다고 가정 =====");

        System.out.println("\n16.\"Goverment\" -> Issuer (Trust Anchor) is creating a Credential Offer for Prover\n");
        String credOfferJson = issuerCreateCredentialOffer(govermentWallet, credDefId).get();
        System.out.println("Credential Offer:\n" + credOfferJson);

        System.out.println("===== 아래부터는 발행자(issuer)가 사용자(holder)에게 Credential Offer 전달 했다고 가정 =====");
        //credOfferJson가 발행자에게 받은거

        System.out.println("\n17. \"Prover\" -> Prover is creating Link Secret\n");
        String linkSecretName = "link_secret";
        String linkSecretId = Anoncreds.proverCreateMasterSecret(proverWallet, linkSecretName).get();

        System.out.println("\n18.\"Prover\" ->  Build GetCredDefRequest \n");
        String receivedSchemaId = new JSONObject(credOfferJson).getString("schema_id");
        String receivedCredDefId = new JSONObject(credOfferJson).getString("cred_def_id");
        String getCredDefRequest = buildGetCredDefRequest(govermentDid, receivedCredDefId).get(); //(발행자DID, credDef Id)
        System.out.println("getCredDefRequest : " + getCredDefRequest);

        System.out.println("\n19.\"Prover\" ->  Send GetCredDefRequest to ledger to get Credential definition\n");
        String getCredDefResponse = submitRequest(pool, getCredDefRequest).get();
        System.out.println("getCredDefResponse : " + getCredDefResponse);

        System.out.println("\n20.\"Prover\" ->  Parse GetCredDefResponse from ledger\n");
        LedgerResults.ParseResponseResult receivedCredDef = parseGetCredDefResponse(getCredDefResponse).get();
        String credDefIdFromLedger = receivedCredDef.getId();
        String credDefJsonFromLedger = receivedCredDef.getObjectJson();
        System.out.println("credDefIdFromLedger :" + credDefIdFromLedger);
        System.out.println("credDefJsonFromLedger : " + credDefJsonFromLedger);

        System.out.println("\n21.\"Prover\" ->  Prover creates Credential Request\n");
        AnoncredsResults.ProverCreateCredentialRequestResult credRequest =
                proverCreateCredentialReq(proverWallet, proverDID, credOfferJson, credDefJsonFromLedger, linkSecretId).get();
        String creReqJson = credRequest.getCredentialRequestJson();
        String creReqMetaJson = credRequest.getCredentialRequestMetadataJson();
        System.out.println("credRequest : " + credRequest);
        System.out.println("creReqJson : " + creReqJson);
        System.out.println("creReqMetaJson : " + creReqMetaJson);

        System.out.println("===== 아래부터는 사용자(Holder)가 발행자(issuer)에게 credRequest 전달 했다고 가정 =====");

        System.out.println("\n22. \"Goverment\" -> Issuer (Trust Anchor) creates Credential for Credential Request\n");
        // Encoded value of non-integer attribute is SHA256 converted to decimal
        // note that encoding is not standardized by Indy except that 32-bit integers are encoded as themselves. IS-786
        String credValuesJson = new JSONObject()
                .put("sex", new JSONObject().put("raw", "male").put("encoded", "594465709955896723921094925839488742869205008160769251991705001"))
                .put("name", new JSONObject().put("raw", "Alex").put("encoded", "1139481716457488690172217916278103335"))
                .put("height", new JSONObject().put("raw", "175").put("encoded", "175"))
                .put("age", new JSONObject().put("raw", "28").put("encoded", "28"))
                .toString();
        AnoncredsResults.IssuerCreateCredentialResult credential =
                issuerCreateCredential(govermentWallet, credOfferJson, creReqJson, credValuesJson, null, -1).get();
        System.out.println(credential.getCredentialJson());
        System.out.println(credential.getRevocId());
        System.out.println(credential.getRevocRegDeltaJson());

        System.out.println("===== 발행자가 VC를 사용자에게 전달 했다고 가정 =====");

        System.out.println("\n23. \"Prover\" -> Prover processes and stores credential\n");
        proverStoreCredential(proverWallet, null, credRequest.getCredentialRequestMetadataJson(),
                credential.getCredentialJson(), credDefJson, credential.getRevocRegDeltaJson());

        /**
         * 스텝6. clean up code
         * 1)만든 지갑들 닫고 삭제.
         * 2)노드풀 닫음.
         * 3)원장설정 삭제.
         */
        System.out.println("\n24. Close and delete wallet\n");
        stewardWallet.closeWallet().get();
        Wallet.deleteWallet(stewardWalletConfig, stewardWalletCredentials).get();
        govermentWallet.closeWallet().get();
        Wallet.deleteWallet(govermentWalletConfig, govermentWalletCredentials).get();
        proverWallet.closeWallet().get();
        Wallet.deleteWallet(proverWalletConfig, proverWalletCredentials).get();

        System.out.println("\n25. Close pool\n");
        pool.closePoolLedger().get();

        System.out.println("\n26. Delete pool ledger config\n");
        Pool.deletePoolLedgerConfig(poolName).get();

        System.out.println("\n################## howto.IssueCredential -> completed ##################\n");
    }


}
