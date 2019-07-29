package iap;

import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ConsumeParams;
import tech.kode.kore.KoreActivity;
import android.content.Intent;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class Billing {

	static {
		System.loadLibrary("kore");
	}

	private static native void onInitComplete();
	private static native void onInitError();
	public static native void onPurchase(int status, String data);
	private static native void onGetProducts(int status, String data);
	public static native void onConsume(int status, String data);

	private static BillingClient billingClient;
	private static PurchasesListener listenerPurchases = new PurchasesListener();

	public static void init() {
		KoreActivity activity = KoreActivity.getInstance();

		billingClient = BillingClient.newBuilder(activity)
			.enablePendingPurchases()
			.setListener(listenerPurchases)
			.build();
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingResponseCode.OK) {
					// The BillingClient is ready. You can query purchases here.
					onInitComplete();
				} else {
					Log.d("kore", "Init error " + billingResult.getResponseCode());
					onInitError();
				}
			}
			@Override
			public void onBillingServiceDisconnected() {
				// Try to restart the connection on the next request to
				// Google Play by calling the startConnection() method.
				Log.d("kore", "Disconnected");
				onInitError();
			}
		});
	}

	static List<SkuDetails> cacheList = new ArrayList();

	public static void getProducts(String ids) {
		List<String> skuList = Arrays.asList(ids.split(","));
		Log.d("kore", skuList.toString());
		SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
		params.setSkusList(skuList).setType(SkuType.INAPP);

		billingClient.querySkuDetailsAsync(params.build(),
			new SkuDetailsResponseListener() {
				@Override
				public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
					int responseCode = billingResult.getResponseCode();
					// Log.d("kore", responseCode + " | " + skuDetailsList);
					if (skuDetailsList != null) {
						for (SkuDetails sku : skuDetailsList) {
							cacheList.remove(sku);
							cacheList.add(sku);
						}
					}
					String jsonArray = arrayToJson(skuDetailsList);
					// Process the result.
					onGetProducts(responseCode, jsonArray);
				}
			}
		);
	}

	public static void purchase(String id) {
		SkuDetails skuDetails = null;
		for (SkuDetails sku : cacheList) {
			if (sku.getSku().equals(id)) {
				skuDetails = sku;
				break;
			}
		}
		if (skuDetails == null) {
			Log.d("kore", id + " sku id not found in cache");
			return;
		}

		KoreActivity activity = KoreActivity.getInstance();
		BillingFlowParams flowParams = BillingFlowParams.newBuilder()
			.setSkuDetails(skuDetails)
			.build();
		BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
		if (result.getResponseCode() != BillingResponseCode.OK)
			Log.d("kore", "launchBillingFlow error: " + result.getResponseCode());
	}

	public static void consume(final String id) {
		Purchase purchase = null;
		for (Purchase cache : PurchasesListener.cacheList) {
			if (cache.getSku().equals(id)) {
				purchase = cache;
				break;
			}
		}
		if (purchase == null) {
			Log.d("kore", id + " purchase id not found in cache");
			return;
		}

		KoreActivity activity = KoreActivity.getInstance();
		ConsumeParams consumeParams = ConsumeParams.newBuilder()
			.setPurchaseToken(purchase.getPurchaseToken())
			.setDeveloperPayload(purchase.getDeveloperPayload())
			.build();

		billingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
			@Override
			public void onConsumeResponse(BillingResult billingResult, String outToken) {
				int responseCode = billingResult.getResponseCode();
				onConsume(responseCode, id);
			}
		});
	}

	public static <T> String arrayToJson(List<T> items) {
		if (items == null) return "[]";
		StringBuffer jsonArray = new StringBuffer("[");
		for (T item : items) {
			if (item instanceof Purchase)
				jsonArray.append(((Purchase)item).getOriginalJson());
			else if (item instanceof SkuDetails)
				jsonArray.append(((SkuDetails)item).getOriginalJson());
			jsonArray.append(",");
		}
		if (!items.isEmpty()) {
			jsonArray.deleteCharAt(jsonArray.length() - 1);
		}
		jsonArray.append("]");
		return jsonArray.toString();
	}

}

class PurchasesListener implements PurchasesUpdatedListener {

	static List<Purchase> cacheList = new ArrayList();

	@Override
	public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
		int responseCode = billingResult.getResponseCode();
		// Log.d("kore", responseCode + " | " + purchases);
		if (purchases != null) {
			for (Purchase purchase : purchases) {
				cacheList.remove(purchase);
				cacheList.add(purchase);
			}
		}
		String jsonArray = Billing.arrayToJson(purchases);
		Billing.onPurchase(responseCode, jsonArray);
	}

}
