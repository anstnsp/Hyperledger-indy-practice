import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.hyperledger.indy.sdk.wallet.WalletExistsException;
import org.json.JSONObject;
import utils.PoolUtils;

import java.util.concurrent.ExecutionException;

import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static org.hyperledger.indy.sdk.ledger.Ledger.submitRequest;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class GettingStarted {

    static void run() throws Exception {
        System.out.println("GettingStarted -> started");

        // Set protocol version 2 to work with Indy Node 1.4
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        /**
         * -- 1.indy-node 구동 및 STEWARD 자격 획득 --
         *
         * STEWARD는 다음 코드를 통해 블록체인에 genesis transaction을 등록할 수 있다.
         * genesis transaction을 이용해 하나의 블록체인을 구성하는 pool을 생성하는 과정.
         * (pool genesis transaction, domain genesis transaction)
         */
        //1-1. Create and Open Pool (인디노드풀에 연결)
        String poolName = PoolUtils.createPoolLedgerConfig("pool1");
        Pool pool = Pool.openPoolLedger(poolName, "{}").get();

        System.out.println("==============================");
        System.out.println("=== Getting Trust Anchor credentials for Faber College, Acme Corp, Thrift Bank and Government  ==");
        System.out.println("==============================");

        System.out.println("\"Sovrin Steward\" -> Create wallet");

        //1-2. steward Create and Open Wallet
        String stewardWalletConfig = new JSONObject().put("id", "stewardWallet").toString();
        String stewardWalletCredentials = new JSONObject().put("key", "steward_wallet_key").toString();
        Wallet.createWallet(stewardWalletConfig, stewardWalletCredentials).get();
        Wallet stewardWallet = Wallet.openWallet(stewardWalletConfig, stewardWalletCredentials).get();

        System.out.println("\"Sovrin Steward\" -> Create and store in Wallet DID from seed");

        //1-3. Create Steward Did
        String seed = "{ \"seed\": \"000000000000000000000000Steward1\" }";
        DidResults.CreateAndStoreMyDidResult stewardDidResult = Did.createAndStoreMyDid(stewardWallet, seed).get();
        String stewardDid = stewardDidResult.getDid();      //DID
        String stewardVerkey = stewardDidResult.getVerkey();//인증키
        System.out.println("stewardDid : " + stewardDid);
        System.out.println("stewardVerkey : " + stewardVerkey);

        /**
         * -- 2.Trust Anchor(Endorser) 등록 및 자격 획득 --
         * Endorser는 각자 자신들이 사용할 DID와 인증키를 생성한 후 STEWARD에게 전달하여 Endorser 등록 요청 함.
         * 이후 STEWARD는 앞서 생성한 STEWARD DID 권한을 가진 DID 와 인증키를 이용해
         * Endorser의 DID 및 인증키를 indy-node블록체인에 등록
         */

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Government Onboarding  ==");
        System.out.println("------------------------------");

        //2-1. Create and Open Goverment Wallet
        String govermentWalletConfig = new JSONObject().put("id", "govermentWallet").toString();
        String govermentWalletCredentials = new JSONObject().put("key", "goverment_wallet_key").toString();
        Wallet.createWallet(govermentWalletConfig, govermentWalletCredentials).get();
        Wallet govermentWallet = Wallet.openWallet(govermentWalletConfig, govermentWalletCredentials).get();

        //2-2. Create Trust Anchor(Endorser) DID
        System.out.println("Creating and storing Trust Anchor DID and Verkey");
        DidResults.CreateAndStoreMyDidResult govermentDidResult = Did.createAndStoreMyDid(govermentWallet, "{}").get();
        String govermentDid = govermentDidResult.getDid();
        String govermentVerkey = govermentDidResult.getVerkey();
        System.out.println("Trust anchor(Goverment) DID: " + govermentDid);
        System.out.println("Trust anchor(Goverment) Verkey: " + govermentVerkey);




        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Government getting Verinym  ==");
        System.out.println("------------------------------");


        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Faber College Onboarding  ==");
        System.out.println("------------------------------");


        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Faber College getting Verinym  ==");
        System.out.println("------------------------------");


        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Acme Corp Onboarding  ==");
        System.out.println("------------------------------");


        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Acme Corp getting Verinym  ==");
        System.out.println("------------------------------");
        System.out.println("==============================");


        System.out.println("== Getting Trust Anchor credentials - Thrift Bank Onboarding  ==");
        System.out.println("------------------------------");


        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Thrift Bank getting Verinym  ==");
        System.out.println("------------------------------");

    }
    static void getVerinym(int poolHandle) {
        System.out.println("\"${to}\" > Create and store in Wallet \"${to}\" new DID");
//        console.log(`\"${to}\" > Create and store in Wallet \"${to}\" new DID"`);

//        DidResults.CreateAndStoreMyDidResult createStewardDidResult = Did.createAndStoreMyDid(stewardWallet, seed).get();

    }


}
