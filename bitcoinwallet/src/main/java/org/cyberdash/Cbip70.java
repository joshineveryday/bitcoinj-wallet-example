package org.cyberdash;

import java.io.File;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.core.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bitcoinj.protocols.payments.PaymentSession;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.wallet.SendRequest;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.uri.BitcoinURI;

public class Cbip70 {
    private static final Logger log = LoggerFactory.getLogger(Cbip70.class);
    final static NetworkParameters params = TestNet3Params.get();
    private static org.bitcoin.protocols.payments.Protos.PaymentRequest paymentRequest;

    public static void main(String[] args) {

        BriefLogFormatter.init();
        WalletAppKit kit = new WalletAppKit(params, new File("."), "walletappkit");

        // Download the blockchain
        kit.startAsync();
        kit.awaitRunning();
        // use kit.connectToLocalhost for regtest
        Address CustomerAddress = kit.wallet().currentReceiveAddress();
        System.out.println("Client's address : " + CustomerAddress);
        System.out.println("Client's Balance : " + kit.wallet().getBalance());

        // I could also update my machine to use the bitcoin: handler
        String url = "bitcoin:n1s7DwSZ4nSh471iPhAGZZbXkiTvtQCi5B?amount=0.00088888&message=payment%20request&r=http://192.168.0.8:3000/request?amount=00088888";

        // Check if wallet has sufficient bitcoins
        if (Float.parseFloat(String.valueOf(kit.wallet().getBalance())) == 0.0) {
            log.warn("Please send some testnet Bitcoins to your address " + kit.wallet().currentReceiveAddress());
        } else {
            System.out.println("Sending payment request!");
            sendPaymentRequest(url, kit);
        }
        // System.out.println("shutting down again");
        // kit.stopAsync();
        // kit.awaitTerminated();
    }

    private static void sendPaymentRequest(String location, WalletAppKit k) {
        try {
            if (location.startsWith("http") || location.startsWith("bitcoin")) {
                BitcoinURI paymentRequestURI = new BitcoinURI(location);
                ListenableFuture<PaymentSession> future = PaymentSession.createFromBitcoinUri(paymentRequestURI, true);
                PaymentSession session = future.get();

                if (session.isExpired()) {
                    log.warn("request expired!"); // payment requests can expire fast?
                } else {
                    send(session, k);
                    // System.exit(1);
                }
            } else {
                // Try to open the payment request as a file.
                log.info("Try to open the payment request as a file");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static void send(PaymentSession session, WalletAppKit k) {
        try {
            log.info("Payment Request");
            log.info("Amount to Pay: " + session.getValue().toFriendlyString());
            log.info("Date: " + session.getDate());
            log.info("Message from merchant: " + session.getMemo());

            PaymentProtocol.PkiVerificationData identity = session.verifyPki();

            if (identity != null) {
                // Merchant identity from the cert
                log.info("Payment Requester: " + identity.displayName);
                log.info("Certificate Authority: " + identity.rootAuthorityName);
            }

            final SendRequest request = session.getSendRequest();
            k.wallet().completeTx(request);
            String customerMemo = "Nice Website";
            Address refundAddress = new Address(params, "mo3LZFYxQgVSM4cDxAUkctNrCMXk5mHfiE");
            ListenableFuture<PaymentProtocol.Ack> future = session.sendPayment(ImmutableList.of(request.tx),
                    refundAddress, customerMemo);

            if (future != null) {
                PaymentProtocol.Ack ack = future.get();
                System.out.println("Memo from merchant: " + ack.getMemo());
                k.wallet().commitTx(request.tx);
            }
            // else {
            // // if bitcoin URI doesn't contain a payment url, we broadcast directly the
            // list
            // // of signed transactions.
            // Wallet.SendResult sendResult = new Wallet.SendResult();
            // sendResult.tx = request.tx;
            // sendResult.broadcast = k.peerGroup().broadcastTransaction(request.tx);
            // sendResult.broadcastComplete = sendResult.broadcast.future();
            // }

        } catch (Exception e) {
            System.err.println("Failed to send payment " + e.getMessage());
            // System.exit(1);
        }
    }
}
