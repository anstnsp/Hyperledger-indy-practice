package howto;

import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;
import utils.PoolUtils;

import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static org.hyperledger.indy.sdk.ledger.Ledger.signAndSubmitRequest;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class RotateKey {

    static void run() throws Exception {
        System.out.println("\n################## howto.RotateKey -> started ##################\n");

        String poolName = "pool";
        String stewardSeed = "000000000000000000000000Steward1";
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        /**
         * 스텝1. ** DID, DID document 등록 **
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
        DidResults.CreateAndStoreMyDidResult govermentDIDResult = Did.createAndStoreMyDid(govermentWallet, "{}").get();
        String govermentDID = govermentDIDResult.getDid();
        String govermentVerkey = govermentDIDResult.getVerkey();
        System.out.println("Trust anchor DID: " + govermentDID);
        System.out.println("Trust anchor Verkey: " + govermentVerkey);

        System.out.println("\n7.\"Steward\" -> Build NYM request to add Trust Anchor to the ledger\n");
        String nymRequest = buildNymRequest(defaultStewardDid, govermentDID, govermentVerkey, null, "TRUST_ANCHOR").get();
        System.out.println("NYM request JSON:\n" + nymRequest);

        System.out.println("\n8.\"Steward\" -> Sending the nym request to ledger\n");
        String nymResponseJson = signAndSubmitRequest(pool, stewardWallet, defaultStewardDid, nymRequest).get();
        System.out.println("NYM transaction response:\n" + nymResponseJson);


        /**
         * 스텝2. ** 지갑 및 원장에 인증키 변경 **
         * 1)지갑 내 존재하는 DID중 인증키 변경을 원하는 DID를 선택한 후 새로운 비대칭키 쌍 생성.(Did.replaceKeysStart)
         * 2)인증키 업데이트를 위한 NymRequest 생성 (buildNymRequest)
         * 3)기존 서명키를 이용해 서명 후 원장에 NymRequest 제출 (signAndSubmitRequest)
         * 4)기존 키쌍을 삭제하고 새로운 키쌍 등록 (Did.replaceKeysApply)
         */
        System.out.println("\n9. Generating new Verkey of Trust Anchor in the wallet\n");
        //identyty_json 매개변수를 통해 키 생성방식과 seed등을 정의한 후 비대칭키 쌍을 생성가능.
        String newGovermentVerkey = Did.replaceKeysStart(govermentWallet, govermentDID, "{}").get();
        System.out.println("New Goverment's Verkey: " + newGovermentVerkey);

        System.out.println("\n10. Building NYM request to update new verkey to ledger\n");
        String nymUpdateRequest = buildNymRequest(govermentDID, govermentDID, newGovermentVerkey, null, "TRUST_ANCHOR").get();
        System.out.println("NYM request:\n" + nymUpdateRequest);

        System.out.println("\n11. Sending NYM request to the ledger\n");
        //업데이트 트랜잭션을 원장에 제출할 때 현재 서명키를 사용해 서명해야 한다.
        String nymUpdateResponse = signAndSubmitRequest(pool, govermentWallet, govermentDID, nymUpdateRequest).get();
        System.out.println("NYM response:\n" + nymUpdateResponse);

        System.out.println("\n12. Applying new Trust Anchor's Verkey in wallet\n");
        Did.replaceKeysApply(govermentWallet, govermentDID);

        /**
         * 스텝3 ** DID document에 serviceEndpoint 추가 **
         * 1)지갑에서 해당 DID에 대한 serviceEndpoint 추가
         * 2)ATTRIB Request 만들어서 원장에 제출
         */

        Did.setEndpointForDid(govermentWallet, govermentDID, "http://test.com", newGovermentVerkey).get();
        String attribRequest = buildAttribRequest(govermentDID, govermentDID, null, null, null).get();
        String attribResponseJson = signAndSubmitRequest(pool, govermentWallet, govermentDID, attribRequest).get();
        System.out.println("ATTRIB transaction response:\n" + attribResponseJson);

        /**
         * 스텝4 ** 원장에 인증키 지갑 인증키와 비교 **
         * 1)지갑에 인증키 획득
         * 2)Goverment DID로 원장 조회
         * 3)원장을 조회해서 얻은 인증키와 지갑 인증키 비교
         */
        System.out.println("\n13. Reading new Verkey from wallet\n");
        String trustAnchorVerkeyFromWallet = Did.keyForLocalDid(govermentWallet, govermentDID).get();

        System.out.println("\n14. Building GET_NYM request to get Trust Anchor from Verkey\n");
        String getNymRequest = buildGetNymRequest(govermentDID, govermentDID).get();
        System.out.println("GET_NYM request:\n" + getNymRequest);

        System.out.println("\n15. Sending GET_NYM request to ledger\n");
        String getNymResponse = submitRequest(pool, getNymRequest).get();
        System.out.println("GET_NYM response:\n" + getNymResponse);

        System.out.println("\n16. Comparing Trust Anchor verkeys\n");
        System.out.println("Written by Steward: " + govermentDID);
        System.out.println("Current from wallet: " + trustAnchorVerkeyFromWallet);
        String responseData = new JSONObject(getNymResponse).getJSONObject("result").getString("data");
        String trustAnchorVerkeyFromLedger = new JSONObject(responseData).getString("verkey");
        System.out.println("Current from ledger: " + trustAnchorVerkeyFromLedger);
        boolean match = !govermentDID.equals(trustAnchorVerkeyFromWallet) && trustAnchorVerkeyFromWallet.equals(trustAnchorVerkeyFromLedger);
        System.out.println("Matching: " + match);


        /**
         * 스텝5. clean up code
         * 1)만든 지갑들 닫고 삭제.
         * 2)노드풀 닫음.
         * 3)원장설정 삭제.
         */
        System.out.println("\n17. Close and delete wallet\n");
        stewardWallet.closeWallet().get();
        Wallet.deleteWallet(stewardWalletConfig, stewardWalletCredentials).get();
        govermentWallet.closeWallet().get();
        Wallet.deleteWallet(govermentWalletConfig, govermentWalletCredentials).get();

        System.out.println("\n18. Close pool\n");
        pool.closePoolLedger().get();

        System.out.println("\n19. Delete pool ledger config\n");
        Pool.deletePoolLedgerConfig(poolName).get();

        System.out.println("\n################## howto.RotateKey -> completed ##################\n");


    }



}
