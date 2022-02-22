package howto;

import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.anoncreds.CredentialsSearchForProofReq;
import org.hyperledger.indy.sdk.blob_storage.BlobStorageReader;
import org.hyperledger.indy.sdk.blob_storage.BlobStorageWriter;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.PoolUtils;

import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.*;
import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static org.junit.Assert.*;
import static utils.EnvironmentUtils.getIndyHomePath;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class NegotiateProofWithRevocation {

    public static void run() throws Exception {
        System.out.println("\n################## howto.NegotiateProofWithRevocation -> started ##################\n");

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
         * 스텝3. Create Schema And Credential Definition
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
        String credDefConfigJson = new JSONObject().put("support_revocation", true).toString(); //VC 폐기지원은 true
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createCredDefResult =
                issuerCreateAndStoreCredentialDef(govermentWallet, govermentDid, schemaJsonFromLedger, credDefTag, null, credDefConfigJson).get();
        String credDefId = createCredDefResult.getCredDefId();
        String credDefJson = createCredDefResult.getCredDefJson();
        System.out.println("Returned Cred Definition ID:\n" + credDefId);
        System.out.println("Returned Cred Definition:\n" + credDefJson);


        //Issuer create Revocation Registry
        /**
         * issuanceType (string enum): credential 해지 전략을 정의합니다. 다음과 같은 값을 가질 수 있습니다.
         * ISSUANCE_BY_DEFAULT: 모든 credential은 처음에 발급된 것으로 간주되므로 해지할 때만 해지 레지스트리를 업데이트(REVOC_REG_ENTRY txn 전송)해야합니다.
         * 해지 레지스트리는 이 경우, 해지된 credential 인덱스만 저장합니다. 예상되는 해지 조치 수가 예상되는 발행 조치 수보다 적은 경우에 사용하는 것이 좋습니다.
         * ISSUANCE_ON_DEMAND: 초기에 발급된 credential이 없으므로 발급 및 해지마다 해지 레지스트리를 업데이트(REVOC_REG_ENTRY txn 전송)해야합니다.
         * 이 경우, 해지 레지스트리는 발급된 credential 인덱스만 저장합니다. 예상되는 발행 조치 수가 예상 해지 조치 수보다 적은 경우 사용하는 것이 좋습니다.
         *
         * revoc_def_type : 해지유형, 현재 CL_ACCUM만 지원
         */
        //===================================[Issuer] Credential definition 생성중 Revocation 때문에 추가 시작 ===================================
        String revRegDefConfig = new JSONObject()
                .put("issuance_type", "ISSUANCE_ON_DEMAND")
                .put("max_cred_num", 5) //폐기가능한 최대 VC개수, max_cred_num 옵션에 따라 tails file의 크기가 달라지며 1백만개 증명서 기준으로 약 200MB 크기의 파일이 생성됨.
                .toString();
        String tailsWriterConfig = new JSONObject()
                .put("base_dir", getIndyHomePath("tails").replace('\\', '/'))
                .put("uri_pattern", "")
                .toString();
        BlobStorageWriter tailsWriter = BlobStorageWriter.openWriter("default", tailsWriterConfig).get();

        String revRegDefTag = "Tag2";
        AnoncredsResults.IssuerCreateAndStoreRevocRegResult createRevRegResult =
                issuerCreateAndStoreRevocReg(govermentWallet, govermentDid, "CL_ACCUM", revRegDefTag, credDefId, revRegDefConfig, tailsWriter).get();
        String revRegId = createRevRegResult.getRevRegId();
        String revRegDefJson = createRevRegResult.getRevRegDefJson();
        String revRegEntryJson = createRevRegResult.getRevRegEntryJson();
        System.out.println("revRegDefId : " + revRegId);
        System.out.println("revocRegDefJson : " + revRegDefJson);
        System.out.println("revocRegEntryJson : " + revRegEntryJson);

        System.out.println("\n14. \"Goverment\" -> Send  \"Goverment gvt\" Credential Definition to Ledger");
        String credDefRequest = buildCredDefRequest(govermentDid, credDefJson).get();
        String credDefResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, credDefRequest).get();
        System.out.println("credDefResponse : " + credDefResponse);

        System.out.println("\n \"Goverment\" -> Send  \"RevocRegDefRequest\" to Ledger");
        String revocRegDefRequest = buildRevocRegDefRequest(govermentDid, revRegDefJson).get();
        String revRegDefResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, revocRegDefRequest).get();
        System.out.println("revRegDefResponse : " +revRegDefResponse);

        System.out.println("\n \"Goverment\" -> Send  \"RevocRegEntryRequest\" to Ledger");
        String revocRegEntryRequest = buildRevocRegEntryRequest(govermentDid, revRegId, "CL_ACCUM", revRegEntryJson).get();
        String revocRegEntryResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, revocRegEntryRequest).get();
        System.out.println("revocRegEntryResponse : " + revocRegEntryResponse);
        //=================================== Credential definition 생성중 Revocation 때문에 추가된 부분 끝 ===================================
        /**
         * 스텝4. Issue Credential
         * 0.[사용자(holder)] -> 사용자는 발행자에게 Credential offer 달라고 요청.
         * 1.[발행인(issuer)] -> 발행인은 Credential offer를 생성하여 사용자(holder)에게 전달.
         * 2.[사용자(holder)] -> 발행인으로부터 받은 VC에 대한 소유권증명을 위한 Link secret 생성.
         * 3.[사용자(holder)] -> 발행인에게 받은 Credential offer를 이용해 발행인의 Credential definition을 요청하는 트랜잭션(buildGetCredDefRequest) 생성한 후
         *                             블록체인에 전송하여 발행인의 Credential definition 획득.
         * 4.[사용자(holder)] -> VC생성요청에 필요한 정보를 모두 획득한 사용자는 prover_create_credential_request를 통해 주어진 Credential offer에 대한 VC발급요청 데이터를 생성하여 발행인 에게 전송.
         * 5.[발행인(issuer)] -> 사용자로 부터 VC요청 받은 발행인은 issuer_create_credential API를 통해 VC를 생성한 후 사용자에게 전달.
         * 6.[사용자(holder)] -> VC를 수신한 사용자는 prover_store_credential API를 이용해 지갑에 VC를 저장함으로써 VC 생성 및 발급 과정 끝.
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
//        AnoncredsResults.IssuerCreateCredentialResult credential = //VC 폐기 없을 때
//                issuerCreateCredential(govermentWallet, credOfferJson, creReqJson, credValuesJson, null, -1).get();

        //===================================[Issuer] Credential(VC) 생성중 Revocation 때문에 추가되는 부분 시작 ===================================
        System.out.println("\n \"Goverment\" -> Issuer (Trust Anchor) open Tails Reader\n");
        tailsWriterConfig = new JSONObject()
                .put("base_dir", getIndyHomePath("tails").replace('\\', '/'))
                .put("uri_pattern", "")
                .toString();
        BlobStorageReader blobStorageReaderCfg = BlobStorageReader.openReader("default", tailsWriterConfig).get();
        int blobStorageReaderHandle = blobStorageReaderCfg.getBlobStorageReaderHandle();

        AnoncredsResults.IssuerCreateCredentialResult credential =  //VC 폐기 있을 때
                issuerCreateCredential(govermentWallet, credOfferJson, creReqJson, credValuesJson, revRegId, blobStorageReaderHandle).get();
        String credentialJson = credential.getCredentialJson();
        String revRegDeltaJson = credential.getRevocRegDeltaJson();
        String credRevId = credential.getRevocId();

        System.out.println("credentialJson : " + credentialJson);
        System.out.println("revRegDeltaJson : " + revRegDeltaJson);
        System.out.println("credRevId : " + credRevId);

        //issuance_type이 ISSUANCE_ON_DEMAND 이므로 VC 발행 때도 buildRevocRegEntryRequest를 원장에 보내준다.
        //issuance_type이 ISSUANCE_BY_DEFAULT 이면 VC(증명서) 해지할 때만 buildRevocRegEntryRequest를 원장에 보내준다.
        System.out.println("\n \"Goverment\" -> Send  \"RevocRegEntryRequest\" to Ledger");
        revocRegEntryRequest = buildRevocRegEntryRequest(govermentDid, revRegId, "CL_ACCUM", revRegDeltaJson).get();
        revocRegEntryResponse = signAndSubmitRequest(pool, govermentWallet, govermentDid, revocRegEntryRequest).get();
        System.out.println("revocRegEntryResponse : " + revocRegEntryResponse);

        System.out.println("===== 발행자가 VC를 사용자에게 전달 했다고 가정 =====");

        System.out.println("\n23. \"Prover\" -> Prover processes and stores credential\n");
        //Holder가 증명서를 저장할때 revocation registry definition 정보가 추가로 필요하다. 이건 credential에 포함된 rev_reg_id 값으로 holder가 직접 pool에서 읽어올수 있고, issuer로부터 전달 받아도 된다.
        proverStoreCredential(proverWallet, null, credRequest.getCredentialRequestMetadataJson(),
                credential.getCredentialJson(), credDefJson, revRegDefJson);
        //=================================== Credential 생성중 Revocation 때문에 추가된 부분 끝 ===================================

        /**
         * 스텝5. VP 생성 및 검증
         * 1.[검증인(verifier)] -> 검증인은 증명요청(ProofRequest) 만들어서 사용자(prover)에게 전달.
         * 2.[사용자(holder,prover)] -> 증명요청(ProofRequest) 수신한 사용자는 보유한 VC 중 주어진 ProofRequest에 해당되는 VC 검색 해 requestedCredentialsJson 만듬.
         * 3.[사용자(holder,prover)] -> 블록체인에서 Schema, Credential definition등 조회 해 VC정보와 함께 VP 생성 후 검증인에게 전송.
         * 4.[검증인(verifier)] -> 검증인은 속성들 확인하고 블록체인에서 Schema, Credential definition등을 조회 해 사용자 VP 검증.
         */
        //=================================== VP 생성 및 검증 중 Revocation 때문에 추가된 부분 시작 ===================================
        //1.[검증인(verifier)] -> 검증인은 증명요청(ProofRequest) 만들어서 사용자(prover)에게 전달.
        System.out.println("\n24.\"Verifier\" -> Create Proof Request to send prover \n");
        long to = System.currentTimeMillis() / 1000; //VC 폐기로 추가 된 부분
        String nonce = generateNonce().get();
        String proofRequestJson = new JSONObject()
                .put("nonce", nonce)
                .put("name", "proof_req_1")
                .put("version", "0.1")
                .put("requested_attributes", new JSONObject()
                        .put("attr1_referent", new JSONObject().put("name", "name"))
                        .put("attr2_referent", new JSONObject().put("name", "sex"))
                        .put("attr3_referent", new JSONObject().put("name", "phone"))
                )
                .put("requested_predicates", new JSONObject()
                        .put("predicate1_referent", new JSONObject()
                                .put("name", "age")
                                .put("p_type", ">=")
                                .put("p_value", 18)
                                .put("restrictions", new JSONObject().put("issuer_did", govermentDid))
                        )
                )
                .put("non_revoked", new JSONObject()   //VC 폐기로 추가 된 부분
                        .put("to", to)

                )
                .toString();
        System.out.println("proofRequestJson : " + proofRequestJson);

        System.out.println("\n ========= 검증인이 proofRequest를 증명인에게 전달했다고 가정 ========= \n");

        //2.[사용자(holder,prover)] -> 증명요청(ProofRequest) 수신한 사용자는 보유한 VC 중 주어진 ProofRequest에 해당되는 VC 검색 해 requestedCredentialsJson 만듬.
        System.out.println("\n25.\"Prover\" -> Prover gets Credentials for Proof Request \n");
        CredentialsSearchForProofReq credentialsSearch = CredentialsSearchForProofReq.open(proverWallet, proofRequestJson, null).get();
        JSONArray credentialsForAttribute1 = new JSONArray(credentialsSearch.fetchNextCredentials("attr1_referent", 100).get());
        String credentialIdForAttribute1 = credentialsForAttribute1.getJSONObject(0).getJSONObject("cred_info").getString("referent");
        System.out.println("credentialsForAttribute1.getJSONObject(0).toString() : " + credentialsForAttribute1.getJSONObject(0).toString());

        JSONArray credentialsForAttribute2 = new JSONArray(credentialsSearch.fetchNextCredentials("attr2_referent", 100).get());
        String credentialIdForAttribute2 = credentialsForAttribute2.getJSONObject(0).getJSONObject("cred_info").getString("referent");
        System.out.println("credentialsForAttribute2.getJSONObject(0).toString() : " + credentialsForAttribute2.getJSONObject(0).toString());

        JSONArray credentialsForAttribute3 = new JSONArray(credentialsSearch.fetchNextCredentials("attr3_referent", 100).get());
        assertEquals(0, credentialsForAttribute3.length());

        JSONArray credentialsForPredicate = new JSONArray(credentialsSearch.fetchNextCredentials("predicate1_referent", 100).get());
        String credentialIdForPredicate = credentialsForPredicate.getJSONObject(0).getJSONObject("cred_info").getString("referent");
        credentialsSearch.close();

        System.out.println("\n\"Prover\" -> Prover create  RevocationState \n");

        String getRevocReqDefRequest = buildGetRevocRegDefRequest(proverDID, revRegId).get(); //VC 폐기로 추가 된 부분
        String getRevocRegDefResponse = submitRequest(pool, getRevocReqDefRequest).get();
        System.out.println("getRevocRegDefResponse : "+ getRevocRegDefResponse);
        LedgerResults.ParseResponseResult revocRegDefFromLedger = parseGetRevocRegDefResponse(getRevocRegDefResponse).get();
        String revocRegDefIdFromLedger = revocRegDefFromLedger.getId();
        String revocRegDefJsonFromLedger = revocRegDefFromLedger.getObjectJson();
        System.out.println("revocRegDefIdFromLedger : " + revocRegDefIdFromLedger);
        System.out.println("revocRegDefJsonFromLedger : " + revocRegDefJsonFromLedger);


        String getRevocRegDeltaRequest = buildGetRevocRegDeltaRequest(proverDID, revRegId, -1, (int) to).get(); //VC 폐기로 추가 된 부분
        String revocRegDeltaResponse = PoolUtils.ensurePreviousRequestApplied(pool, getRevocRegDeltaRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });

        System.out.println("revocRegDeltaResponse: " + revocRegDeltaResponse);
        LedgerResults.ParseRegistryResponseResult revocRegDeltaFromLedger = parseGetRevocRegDeltaResponse(revocRegDeltaResponse).get();
        String revocRegDeltaIdFromLedger = revocRegDeltaFromLedger.getId();
        String revocRegDeltaJsonFromLedger = revocRegDeltaFromLedger.getObjectJson();
        long revocRegDeltaTimeStampFromLedger = revocRegDeltaFromLedger.getTimestamp();
        System.out.println("revocRegDeltaIdFromLedger : " + revocRegDeltaIdFromLedger);
        System.out.println("revocRegDeltaJsonFromLedger : " + revocRegDeltaJsonFromLedger);
        System.out.println("revocRegDeltaTimeStampFromLedger : " + revocRegDeltaTimeStampFromLedger);
        //credRevId 값을 발행자가 홀더에게 proofReq랑 같이 전달해야하나?
        String revStateJson = createRevocationState(blobStorageReaderHandle, revocRegDefJsonFromLedger, revocRegDeltaJsonFromLedger, revocRegDeltaTimeStampFromLedger, credRevId).get();  //VC 폐기로 추가 된 부분

        System.out.println("\n26.\"Prover\" -> Prover create Proof \n");
        String selfAttestedValue = "010-1234-1133";
        String requestedCredentialsJson = new JSONObject()
                .put("self_attested_attributes", new JSONObject().put("attr3_referent", selfAttestedValue))
                .put("requested_attributes", new JSONObject()
                        .put("attr1_referent", new JSONObject()
                                .put("cred_id", credentialIdForAttribute1)
                                .put("revealed", true)
                                .put("timestamp", revocRegDeltaTimeStampFromLedger)  //VC 폐기로 추가 된 부분
                        )
                        .put("attr2_referent", new JSONObject()
                                .put("cred_id", credentialIdForAttribute2)
                                .put("revealed", false)
                                .put("timestamp", revocRegDeltaTimeStampFromLedger)  //VC 폐기로 추가 된 부분
                        )
                )
                .put("requested_predicates", new JSONObject()
                        .put("predicate1_referent", new JSONObject()
                                .put("cred_id",credentialIdForPredicate)
                                .put("timestamp", revocRegDeltaTimeStampFromLedger) //VC 폐기로 추가 된 부분
                        )
                )
                .toString();
        //3.[사용자(holder,prover)] -> 블록체인에서 Schema, Credential definition등을 조회 해 VC정보와 함께 VP 생성 후 검증인에게 전송.
        System.out.println("\n26.\"Prover\" -> get Schema And Credential definition From Ledger \n");

        LedgerResults.ParseResponseResult schemaFromLedger = IndyUtil.getSchema(pool, govermentDid, receivedSchemaId);
        LedgerResults.ParseResponseResult credDefFromLedger = IndyUtil.getCredDef(pool, govermentDid, receivedCredDefId);

        String schemasForProver = new JSONObject().put(schemaFromLedger.getId(), new JSONObject(schemaFromLedger.getObjectJson())).toString();
        String credentialDefsForProver =  new JSONObject().put(credDefFromLedger.getId(),  new JSONObject(credDefFromLedger.getObjectJson())).toString();
        String revocStates = new JSONObject().put(revRegId, new JSONObject().put(String.valueOf(revocRegDeltaTimeStampFromLedger), new JSONObject(revStateJson))).toString();

        System.out.println("\n27.\"Prover\" -> Prover create Proof \n");
        String proofJson = ""; //검증인에게 전송할 값

        try {
            proofJson = proverCreateProof(proverWallet, proofRequestJson, requestedCredentialsJson,
                    linkSecretId, schemasForProver, credentialDefsForProver, revocStates).get();
        } catch (Exception e){
            System.out.println("에러어어어");
            e.printStackTrace();
        }

        System.out.println("\n ========= 사용자가 Proof를 검증인에게 전달 했다고 가정 ========= \n");
        JSONObject proof = new JSONObject(proofJson);
        System.out.println("proof : " + proof);

        /**
         * "requested_proof": {
         *     "predicates": {
         *         "predicate1_referent": {
         *             "sub_proof_index": 0
         *         }
         *     },
         *     "self_attested_attrs": {
         *         "attr3_referent": "010-1234-1133"
         *     },
         *     "revealed_attrs": {
         *         "attr1_referent": {
         *             "raw": "Alex",
         *             "sub_proof_index": 0,
         *             "encoded": "1139481716457488690172217916278103335"
         *         }
         *     },
         *     "unrevealed_attrs": {
         *         "attr2_referent": {
         *             "sub_proof_index": 0
         *         }
         *     }
         * }
         */
        //4.[검증인(verifier)] -> 검증인은 블록체인에서 속성확인 및 Schema, Credential definition등을 조회 해 사용자 VP 검증.
        System.out.println("\n28.\"Verifier\" -> Verify Proof \n");
        String verifierDid = "VsKV7grR1BUE29mG2Fm2kX";
        JSONObject identifier = proof.getJSONArray("identifiers").getJSONObject(0); //identifier : {"rev_reg_id":"ReTaRQWhWvtYd4GjeW1yFZ:4:ReTaRQWhWvtYd4GjeW1yFZ:3:CL:218:test:CL_ACCUM:Tag2","schema_id":"ReTaRQWhWvtYd4GjeW1yFZ:2:IDcard:1.0","cred_def_id":"ReTaRQWhWvtYd4GjeW1yFZ:3:CL:218:test","timestamp":1645468273}
        System.out.println("identifier : " + identifier);
        System.out.println("revRegDefId: " + revRegId);
        JSONObject revealedAttr1 = proof.getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr1_referent");
        System.out.println("revealedAttr1.toString(): " + revealedAttr1.toString());
        assertEquals("Alex", revealedAttr1.getString("raw"));

        assertNotNull(proof.getJSONObject("requested_proof").getJSONObject("unrevealed_attrs").getJSONObject("attr2_referent").getInt("sub_proof_index"));

        assertEquals(selfAttestedValue, proof.getJSONObject("requested_proof").getJSONObject("self_attested_attrs").getString("attr3_referent"));

        LedgerResults.ParseResponseResult schemaFromLedgerForVerifier = IndyUtil.getSchema(pool, verifierDid, identifier.getString("schema_id"));
        LedgerResults.ParseResponseResult credDefFromLedgerForVerifier = IndyUtil.getCredDef(pool, verifierDid, identifier.getString("cred_def_id"));
        LedgerResults.ParseResponseResult revocRegDefFromLedgerForVerifier = IndyUtil.getRevocRegDef(pool, verifierDid, identifier.getString("rev_reg_id"));
        LedgerResults.ParseRegistryResponseResult revocRegFromLedger = IndyUtil.getRevocReg(pool, verifierDid, identifier.getString("rev_reg_id"), identifier.getInt("timestamp"));
        String revocRegIdFromLedger = revocRegFromLedger.getId();
        String revocRegJsonFromLedger = revocRegFromLedger.getObjectJson();
        long revocRegTimestamp = revocRegFromLedger.getTimestamp();

        //revocRegDefFromLedgerForVerifier.getId() , revocRegFromLedger.getId() 같은거임
        String schemasForVerifier = new JSONObject().put(schemaFromLedgerForVerifier.getId(), new JSONObject(schemaFromLedgerForVerifier.getObjectJson())).toString();
        String credentialDefsForVerifier =  new JSONObject().put(credDefFromLedgerForVerifier.getId(),  new JSONObject(credDefFromLedgerForVerifier.getObjectJson())).toString();
        String revocRegDefsJson = new JSONObject().put(revocRegDefFromLedgerForVerifier.getId(), new JSONObject(revocRegDefFromLedgerForVerifier.getObjectJson())).toString();
        String revocRegsJson = new JSONObject().put(revocRegIdFromLedger, new JSONObject().put(String.valueOf(revocRegTimestamp), new JSONObject(revocRegJsonFromLedger))).toString();

        //String revocRegDefs = new JSONObject().toString(); //폐기여부를 검증해야 하는 VC일 경우 블록체인에서 가져와야 함.
        //String revocRegs = new JSONObject().toString(); //폐기여부를 검증해야 하는 VC일 경우 블록체인에서 가져와야 함.

        Boolean valid = verifierVerifyProof(proofRequestJson, proofJson, schemasForVerifier, credentialDefsForVerifier, revocRegDefsJson, revocRegsJson).get();
        assertTrue(valid);

        /**
         * 스텝5. VC 폐기 후 검증
         * 0.[발행자(issuer)] -> VC 폐기
         * 1.[사용자(holder,prover)] -> 증명요청(ProofRequest) 수신한 사용자는 보유한 VC 중 주어진 ProofRequest에 해당되는 VC 검색 해 requestedCredentialsJson 만듬.
         * 2.[사용자(holder,prover)] -> 블록체인에서 Schema, Credential definition등 조회 해 VC정보와 함께 VP 생성 후 검증인에게 전송.
         * 3.[검증인(verifier)] -> 검증인은 속성들 확인하고 블록체인에서 Schema, Credential definition등을 조회 해 사용자 VP 검증.
         */
        //=================================== [Issuer] -> VC 폐기 (Credential revoke) 시작 ===================================
        blobStorageReaderCfg = BlobStorageReader.openReader("default", tailsWriterConfig).get();
        blobStorageReaderHandle = blobStorageReaderCfg.getBlobStorageReaderHandle();

        // Issuer revokes credential
        System.out.println("\n \"Issuer\" -> revoke Credential \n");
        revRegDeltaJson = issuerRevokeCredential(govermentWallet, blobStorageReaderCfg.getBlobStorageReaderHandle()
                , revRegId, credRevId).get();

        // Issuer post RevocationRegistryDelta to Ledger
        System.out.println("\n \"Issuer\" -> Send RevocRegEntryRequest to Ledger \n");
        revocRegEntryRequest = buildRevocRegEntryRequest(govermentDid, revRegId, "CL_ACCUM", revRegDeltaJson).get();
        signAndSubmitRequest(pool, govermentWallet, govermentDid, revocRegEntryRequest).get();
        //=================================== 발행자(Issuer)가 VC 폐기 (Credential revoke) 끝 ===================================

        // Verifying Prover Credential after Revocation
        Thread.sleep(3000);

        //=================================== 사용자(Holeder, Prover)가 VP 생성 후 전달===================================
        long from = to; //VC 발행시점
        to = System.currentTimeMillis() / 1000; //현재

        System.out.println("\n \"Prover\" -> gets RevocationRegistryDelta from Ledger \n");
        getRevocRegDeltaRequest = buildGetRevocRegDeltaRequest(proverDID, revRegId, (int) from, (int) to).get();
        String getRevocRegDeltaResponse = PoolUtils.ensurePreviousRequestApplied(pool, getRevocRegDeltaRequest, innerResponse -> {
            JSONObject innerResponseObject = new JSONObject(innerResponse);
            return !innerResponseObject.getJSONObject("result").isNull("seqNo");
        });
        revocRegDeltaFromLedger = parseGetRevocRegDeltaResponse(getRevocRegDeltaResponse).get();

        revocRegDeltaIdFromLedger = revocRegDeltaFromLedger.getId();
        revocRegDeltaJsonFromLedger = revocRegDeltaFromLedger.getObjectJson();
        revocRegDeltaTimeStampFromLedger = revocRegDeltaFromLedger.getTimestamp();

        System.out.println("\n \"Prover\" -> gets getRevocRegDef from Ledger \n");
        LedgerResults.ParseResponseResult getRevRegDefResponse = IndyUtil.getRevocRegDef(pool, proverDID, revocRegDeltaIdFromLedger);
        revRegDefJson = getRevRegDefResponse.getObjectJson();

        revStateJson = Anoncreds.createRevocationState(blobStorageReaderHandle,
                revRegDefJson, revocRegDeltaJsonFromLedger, revocRegDeltaTimeStampFromLedger, credRevId).get();

        selfAttestedValue = "010-1234-1133";
        requestedCredentialsJson = new JSONObject()
                .put("self_attested_attributes", new JSONObject().put("attr3_referent", selfAttestedValue))
                .put("requested_attributes", new JSONObject()
                        .put("attr1_referent", new JSONObject()
                                .put("cred_id", credentialIdForAttribute1)
                                .put("revealed", true)
                                .put("timestamp", revocRegDeltaTimeStampFromLedger)  //VC 폐기로 추가 된 부분
                        )
                        .put("attr2_referent", new JSONObject()
                                .put("cred_id", credentialIdForAttribute2)
                                .put("revealed", false)
                                .put("timestamp", revocRegDeltaTimeStampFromLedger)  //VC 폐기로 추가 된 부분
                        )
                )
                .put("requested_predicates", new JSONObject()
                        .put("predicate1_referent", new JSONObject()
                                .put("cred_id",credentialIdForPredicate)
                                .put("timestamp", revocRegDeltaTimeStampFromLedger) //VC 폐기로 추가 된 부분
                        )
                )
                .toString();

        System.out.println("\n \"Prover\" -> create RevocationState \n");
        revocStates = new JSONObject().put(revRegId, new JSONObject().
                put(String.valueOf(revocRegDeltaTimeStampFromLedger), new JSONObject(revStateJson))).toString();

        System.out.println("\n \"Prover\" -> create proof \n");
        proofJson = proverCreateProof(proverWallet, proofRequestJson, requestedCredentialsJson,
                linkSecretId, schemasForProver, credentialDefsForProver, revocStates).get();

        //=================================== 검증자(Verifier)가 VP 검증 ===================================
        proof = new JSONObject(proofJson);
        identifier = proof.getJSONArray("identifiers").getJSONObject(0);

        System.out.println("\n \"Verifier\" -> get RevocReg From Ledger\n");
        revocRegFromLedger = IndyUtil.getRevocReg(pool, verifierDid, identifier.getString("rev_reg_id"), identifier.getInt("timestamp"));
        revocRegIdFromLedger = revocRegFromLedger.getId();
        revocRegJsonFromLedger = revocRegFromLedger.getObjectJson();
        revocRegTimestamp = revocRegFromLedger.getTimestamp();

        revocRegsJson = new JSONObject().put(revocRegIdFromLedger, new JSONObject().
                put(String.valueOf(revocRegTimestamp), new JSONObject(revocRegJsonFromLedger))).toString();

        System.out.println("\n \"Verifier\" -> verify Proof \n");
        valid = verifierVerifyProof(proofRequestJson, proofJson, schemasForVerifier,
                credentialDefsForVerifier, revocRegDefsJson, revocRegsJson).get();

        assertFalse(valid);
        //=================================== 검증자(Verifier)가 VP 끝 ===================================
        /**
         * 스텝6. clean up code
         * 1)만든 지갑들 닫고 삭제.
         * 2)노드풀 닫음.
         * 3)원장설정 삭제.
         */
        System.out.println("\n29. Close and delete wallet\n");
        stewardWallet.closeWallet().get();
        Wallet.deleteWallet(stewardWalletConfig, stewardWalletCredentials).get();
        govermentWallet.closeWallet().get();
        Wallet.deleteWallet(govermentWalletConfig, govermentWalletCredentials).get();
        proverWallet.closeWallet().get();
        Wallet.deleteWallet(proverWalletConfig, proverWalletCredentials).get();

        System.out.println("\n30. Close pool\n");
        pool.closePoolLedger().get();

        System.out.println("\n31. Delete pool ledger config\n");
        Pool.deletePoolLedgerConfig(poolName).get();

        System.out.println("\n################## howto.NegotiateProofWithRevocation -> completed ##################\n");
    }



}
