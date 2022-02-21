package howto;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;
import utils.PoolUtils;

import java.util.concurrent.ExecutionException;

import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static utils.PoolUtils.PROTOCOL_VERSION;


/*
Example demonstrating how to add DID with the role of Trust Anchor as Steward.
Uses seed to obtain Steward's DID which already exists on the ledger.
Then it generates new DID/Verkey pair for Trust Anchor.
Using Steward's DID, NYM transaction request is built to add Trust Anchor's DID and Verkey
on the ledger with the role of Trust Anchor.
Once the NYM is successfully written on the ledger, it generates new DID/Verkey pair that represents
a client, which are used to create GET_NYM request to query the ledger and confirm Trust Anchor's Verkey.
For the sake of simplicity, a single wallet is used. In the real world scenario, three different wallets
would be used and DIDs would be exchanged using some channel of communication
*/

// docs : https://github.com/hyperledger/indy-sdk/blob/v1.16.0/docs/how-tos/write-did-and-query-verkey/java/WriteDIDAndQueryVerkey.java
public class WriteDID {

    public static void run() throws Exception {

        System.out.println("\n################## howto.WriteDIDAndQueryVerkey -> started ##################\n");

        // Set protocol version 2 to work with Indy Node 1.4
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        //Create and Open Pool (인디노드풀에 연결)
        String poolName = PoolUtils.createPoolLedgerConfig("pool100");
        Pool pool = Pool.openPoolLedger(poolName, "{}").get();

        /**
         * 1.DID, VC, 인증키 등을 보관할 지갑 생성 및 실행.
         */
        System.out.println("\"Sovrin Steward\" -> Create and Open steward Wallet");
        String stewardWalletConfig = new JSONObject().put("id", "stewardWallet").toString();
        String stewardWalletCredentials = new JSONObject().put("key", "steward_wallet_key").toString();
        Wallet.createWallet(stewardWalletConfig, stewardWalletCredentials).get();
        Wallet stewardWallet = Wallet.openWallet(stewardWalletConfig, stewardWalletCredentials).get();


        /**
         * 2.DID 와 verKey(인증키) 생성 및 지갑에 저장.
         */
        //Create and Store Steward Did & verKey
        System.out.println("\"Sovrin Steward\" -> Create and store DID from seed in Wallet");
        String didJson = "{ \"seed\": \"000000000000000000000000Steward1\" }";
        DidResults.CreateAndStoreMyDidResult stewardDidResult = Did.createAndStoreMyDid(stewardWallet, didJson).get();
        String stewardDid = stewardDidResult.getDid();      //DID
        String stewardVerkey = stewardDidResult.getVerkey();//인증키
        System.out.println("stewardDid : " + stewardDid);
        System.out.println("stewardVerkey : " + stewardVerkey);

        System.out.println("\"Goverment\" -> Create and Open goverment Wallet");
        String govermentWalletConfig = new JSONObject().put("id", "govermentWallet").toString();
        String govermentWalletCredentials = new JSONObject().put("key", "goverment_wallet_key").toString();
        Wallet.createWallet(govermentWalletConfig, govermentWalletCredentials).get();
        Wallet govermentWallet = Wallet.openWallet(govermentWalletConfig, govermentWalletCredentials).get();

        System.out.println("\"Goverment\" -> Creating and storing Trust Anchor DID and Verkey");
        DidResults.CreateAndStoreMyDidResult govermentDidResult = Did.createAndStoreMyDid(govermentWallet, "{}").get();
        String govermentDid = govermentDidResult.getDid();
        String govermentVerkey = govermentDidResult.getVerkey();
        System.out.println("Trust anchor(Goverment) DID: " + govermentDid);
        System.out.println("Trust anchor(Goverment) Verkey: " + govermentVerkey);


        /**
         *  3.블록체인에 DID를 등록하기 위한 NYM 트랜잭셩 생성.(goverment에게 DID 및 인증키를 전달 받고 실행)
         *  -> 하나의 소스코드에서 표현하다보니 로컬변수로 goverment의 DID 및 verKey를 그냥 사용 했지만
         *     실제로는 DID 및 verKey 전달(goverment -> Steward)을 위한 프로토콜이 필요하다.
         *     (Hyperledger Aries에서 개발중인 DIDComm 프로토콜이 쓰이는듯 하다)
         */
        System.out.println("\"Sovrin Steward\" -> Build NYM request to add Trust Anchor to the ledger");
        //buildNymRequest(트랜잭셩 생성자의DID, 블록체인에등록할 DID, 블록체인에등록할 verKey, 별명, 역할)
        String nymRequest = buildNymRequest(stewardDid, govermentDid, govermentVerkey, null, "TRUST_ANCHOR").get();
        System.out.println("NYM request JSON:\n" + nymRequest);

        /**
         *  4.생성한 NYM트랜잭션을 블록체인으로 전송.
         */
        System.out.println("\"Sovrin Steward\" -> Sending the nym request to ledger");
        //signAndSubmitRequest(어떤블록체인네트워크에 생성할지 명시한 pool_handle, 트랜잭션생성자 지갑,트랜잭션생성자 DID, NYM request)
        String nymResponseJson = signAndSubmitRequest(pool, stewardWallet, stewardDid, nymRequest).get();
        System.out.println("NYM transaction response:\n" + nymResponseJson);


        System.out.println("\"Client\" -> Create and Open client Wallet");
//        Wallet clientWallet = createAndOpenWallet("client");
        String clientWalletConfig = new JSONObject().put("id", "clientWallet").toString();
        String clientWalletCredentials = new JSONObject().put("key", "client_wallet_key").toString();
        Wallet.createWallet(clientWalletConfig, clientWalletCredentials).get();
        Wallet clientWallet = Wallet.openWallet(clientWalletConfig, clientWalletCredentials).get();


        System.out.println("\"Client\" -> Generating and storing DID and Verkey to query the ledger with");
        DidResults.CreateAndStoreMyDidResult clientResult = Did.createAndStoreMyDid(clientWallet, "{}").get();
        String clientDID = clientResult.getDid();
        String clientVerkey = clientResult.getVerkey();
        System.out.println("Client DID: " + clientDID);
        System.out.println("Client Verkey: " + clientVerkey);

        System.out.println("\"Client\" -> Building the GET_NYM request to query Trust Anchor's Verkey as the Client");
        String getNymRequest = buildGetNymRequest(clientDID, govermentDid).get();
        System.out.println("GET_NYM request json:\n" + getNymRequest);

        System.out.println("\"Client\" -> Sending the GET_NYM request to the ledger");
        String getNymResponse = submitRequest(pool, getNymRequest).get();
        System.out.println("GET_NYM response json:\n" + getNymResponse);

        System.out.println("\nComparing Trust Anchor Verkey as written by Steward and as retrieved in Client's query\n");
        String responseData = new JSONObject(getNymResponse).getJSONObject("result").getString("data");
        String trustAnchorVerkeyFromLedger = new JSONObject(responseData).getString("verkey");
        System.out.println("Written by Steward: " + govermentVerkey);
        System.out.println("Queried from Ledger: " + trustAnchorVerkeyFromLedger);
        System.out.println("Matching: " + govermentVerkey.equals(trustAnchorVerkeyFromLedger));

        System.out.println("\nClose and delete wallet\n");
        stewardWallet.closeWallet().get();
        Wallet.deleteWallet(stewardWalletConfig, stewardWalletCredentials).get();
        govermentWallet.closeWallet().get();
        Wallet.deleteWallet(govermentWalletConfig, govermentWalletCredentials).get();
        clientWallet.closeWallet().get();
        Wallet.deleteWallet(clientWalletConfig, clientWalletCredentials).get();

        System.out.println("\nClose pool\n");
        pool.closePoolLedger().get();


        System.out.println("\nDelete pool ledger config\n");
        Pool.deletePoolLedgerConfig(poolName).get();

        System.out.println("\n################## howto.WriteDIDAndQueryVerkey -> completed ##################\n");
    }






    static Wallet createAndOpenWallet(String identity) throws Exception {
        System.out.println("["+identity+"]-> Create And Open wallet" + "["+identity+"]");
        String walletConfig = new JSONObject().put("id", identity+"Wallet").toString();
        String walletCredentials = new JSONObject().put("key", identity+"_wallet_key").toString();
        Wallet.createWallet(walletConfig, walletCredentials).get();
        return Wallet.openWallet(walletConfig, walletCredentials).get();
    }

    static void getVerinym(String from, String identity) {
//        createWallet("goverment");
    }

    static void sendNym(Pool pool, Wallet fromWallet, String did,
                        String newDid , String newKey, String role) throws IndyException, ExecutionException, InterruptedException {
        String nymRequest = buildNymRequest(did, newDid, newKey, null, role).get();
        System.out.println("NYM request JSON:\n" + nymRequest);
        String nymResponseJson = signAndSubmitRequest(pool, fromWallet, did, nymRequest).get();
        System.out.println("NYM transaction response:\n" + nymResponseJson);
    }

    static void sendGetNym(Pool pool, String clientDid, String endorserDid) throws IndyException, ExecutionException, InterruptedException {
        System.out.println("\"Client\" -> Building the GET_NYM request to query Trust Anchor's Verkey as the Client");
        String getNymRequest = buildGetNymRequest(clientDid, endorserDid).get();
        System.out.println("GET_NYM request json:\n" + getNymRequest);

        System.out.println("\"Client\" -> Sending the GET_NYM request to the ledger");
        String getNymResponse = submitRequest(pool, getNymRequest).get();
        System.out.println("GET_NYM response json:\n" + getNymResponse);
    }


    //    async function onboarding(poolHandle, From, fromWallet, fromDid, to, toWallet,
//    toWalletConfig, toWalletCredentials) {
    //let [governmentWallet, stewardGovernmentKey, governmentStewardDid, governmentStewardKey] =
    // await onboarding(poolHandle, "Sovrin Steward", stewardWallet, stewardDid, "Government",
    // null, governmentWalletConfig, governmentWalletCredentials);
    static void onBoarding(Pool pool, String fromIdentity, Wallet fromWallet, String fromDid,
                           String toIdentity, Wallet toWallet, String walletConfig, String walletCredentials) throws Exception {
        System.out.println(fromIdentity + "-> Create and store in Wallet ["+ fromIdentity + "  " + toIdentity +"] DID");

        DidResults.CreateAndStoreMyDidResult fromToDidResult = Did.createAndStoreMyDid(fromWallet, "{}").get();
        String fromToDid = fromToDidResult.getDid();      //DID
        String fromToVerkey = fromToDidResult.getVerkey();//인증키

        System.out.println("["+fromIdentity+"] > Send Nym to Ledger for [" + fromIdentity + toIdentity + "] DID");
        sendNym(pool, fromWallet, fromDid, fromToDid, fromToVerkey, null);

        System.out.println("["+fromIdentity+"]" + " > Send connection request to "+ toIdentity + " with [" + fromIdentity + toIdentity + "] DID and nonce");

        JSONObject connectionRequest = new JSONObject();
        connectionRequest.put("did", fromToDid);
        connectionRequest.put("nonce", 123456789);

        if (toWallet == null) {
            toWallet = createAndOpenWallet(toIdentity);
        }

        System.out.println(toIdentity + "-> Create and store in Wallet ["+ toIdentity + "  " + fromIdentity +"] DID");
        DidResults.CreateAndStoreMyDidResult toFromDidResult = Did.createAndStoreMyDid(toWallet, "{}").get();
        String toFromDid = toFromDidResult.getDid();      //DID
        String toFromVerkey = toFromDidResult.getVerkey();//인증키

        System.out.println("["+toIdentity+"] > Get key for did from [" + fromIdentity + " connection request");



//        await sendNym(poolHandle, fromWallet, fromDid, fromToDid, fromToKey, null);
        //async function sendNym(poolHandle, walletHandle, Did, newDid, newKey, role)

//            console.log(`\"${From}\" > Create and store in Wallet \"${From} ${to}\" DID`);
//                    let [fromToDid, fromToKey] = await indy.createAndStoreMyDid(fromWallet, {});
//
//            console.log(`\"${From}\" > Send Nym to Ledger for \"${From} ${to}\" DID`);
//                    await sendNym(poolHandle, fromWallet, fromDid, fromToDid, fromToKey, null);
//
//
//            console.log(`\"${From}\" > Send connection request to ${to} with \"${From} ${to}\" DID and nonce`);
//                    let connectionRequest = {
//                            did: fromToDid,
//                    nonce: 123456789
//    };
//
//            if (!toWallet) {
//                console.log(`\"${to}\" > Create wallet"`);
//                try {
//                    await indy.createWallet(toWalletConfig, toWalletCredentials);
//                } catch(e) {
//                    if(e.message !== "WalletAlreadyExistsError") {
//                        throw e;
//                    }
//                }
//                toWallet = await indy.openWallet(toWalletConfig, toWalletCredentials);
//            }
//
//            console.log(`\"${to}\" > Create and store in Wallet \"${to} ${From}\" DID`);
//                    let [toFromDid, toFromKey] = await indy.createAndStoreMyDid(toWallet, {});
//
//            console.log(`\"${to}\" > Get key for did from \"${From}\" connection request`);
//                    let fromToVerkey = await indy.keyForDid(poolHandle, toWallet, connectionRequest.did);
//
//            console.log(`\"${to}\" > Anoncrypt connection response for \"${From}\" with \"${to} ${From}\" DID, verkey and nonce`);
//                    let connectionResponse = JSON.stringify({
//                            'did': toFromDid,
//                    'verkey': toFromKey,
//                    'nonce': connectionRequest['nonce']
//    });
//            let anoncryptedConnectionResponse = await indy.cryptoAnonCrypt(fromToVerkey, Buffer.from(connectionResponse, 'utf8'));
//
//            console.log(`\"${to}\" > Send anoncrypted connection response to \"${From}\"`);
//
//                    console.log(`\"${From}\" > Anondecrypt connection response from \"${to}\"`);
//                            let decryptedConnectionResponse = JSON.parse(Buffer.from(await indy.cryptoAnonDecrypt(fromWallet, fromToKey, anoncryptedConnectionResponse)));
//
//            console.log(`\"${From}\" > Authenticates \"${to}\" by comparision of Nonce`);
//            if (connectionRequest['nonce'] !== decryptedConnectionResponse['nonce']) {
//                throw Error("nonces don't match!");
//            }
//
//            console.log(`\"${From}\" > Send Nym to Ledger for \"${to} ${From}\" DID`);
//                    await sendNym(poolHandle, fromWallet, fromDid, decryptedConnectionResponse['did'], decryptedConnectionResponse['verkey'], null);
//
//            return [toWallet, fromToKey, toFromDid, toFromKey, decryptedConnectionResponse];
    }

}