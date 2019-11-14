package iap;

import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.Purchase.PurchaseState;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.AcknowledgePurchaseParams;
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
	private static native void onGetPurchases(int status, String data);
	private static native void onConsume(int status, String data);
	private static native void onAcknowledge(int status, String data);

	private static BillingClient billingClient;
	private static PurchasesListener listenerPurchases = new PurchasesListener();

	public static void init() {
		final KoreActivity activity = KoreActivity.getInstance();

		billingClient = BillingClient.newBuilder(activity)
			.enablePendingPurchases()
			.setListener(listenerPurchases)
			.build();
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(BillingResult billingResult) {
				if (billingResult.getResponseCode() == BillingResponseCode.OK) {
					// The BillingClient is ready. You can query purchases here.
					activity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							onInitComplete();
						}
					});
				} else {
					Log.d("Kinc", "Init error " + billingResult.getResponseCode());
					activity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							onInitError();
						}
					});
				}
			}
			@Override
			public void onBillingServiceDisconnected() {
				// Try to restart the connection on the next request to
				// Google Play by calling the startConnection() method.
				Log.d("Kinc", "Disconnected");
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onInitError();
					}
				});
			}
		});
	}

	static List<SkuDetails> cacheList = new ArrayList();

	public static void getProducts(String ids) {
		List<String> skuList = Arrays.asList(ids.split(","));
		Log.d("Kinc", skuList.toString());
		SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
		params.setSkusList(skuList).setType(SkuType.INAPP);

		billingClient.querySkuDetailsAsync(params.build(),
			new SkuDetailsResponseListener() {
				@Override
				public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
					final int responseCode = billingResult.getResponseCode();
					// Log.d("Kinc", responseCode + " | " + skuDetailsList);
					if (skuDetailsList != null) {
						for (SkuDetails sku : skuDetailsList) {
							cacheList.remove(sku);
							cacheList.add(sku);
						}
					}
					final String jsonArray = arrayToJson(skuDetailsList);
					// Process the result.
					KoreActivity.getInstance().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							onGetProducts(responseCode, jsonArray);
						}
					});
				}
			}
		);
	}

	public static void getPurchases() {
		PurchasesResult result = billingClient.queryPurchases(SkuType.INAPP);
		List<Purchase> purchases = result.getPurchasesList();
		if (purchases != null) {
			for (Purchase purchase : purchases) {
				PurchasesListener.cacheList.remove(purchase);
				PurchasesListener.cacheList.add(purchase);
			}
		}
		final String jsonArray = arrayToJson(purchases);
		final int code = result.getResponseCode();

		KoreActivity.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				onGetPurchases(code, jsonArray);
			}
		});
	}

	public static void purchase(final String id) {
		final KoreActivity activity = KoreActivity.getInstance();
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				SkuDetails skuDetails = null;
				for (SkuDetails sku : cacheList) {
					if (sku.getSku().equals(id)) {
						skuDetails = sku;
						break;
					}
				}
				if (skuDetails == null) {
					Log.d("Kinc", id + " sku id not found in cache");
					return;
				}

				BillingFlowParams flowParams = BillingFlowParams.newBuilder()
					.setSkuDetails(skuDetails)
					.build();
				BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
				if (result.getResponseCode() != BillingResponseCode.OK)
					Log.d("Kinc", "launchBillingFlow error: " + result.getResponseCode());
			}
		});
	}

	public static void consume(final String id) {
		Purchase purchase = findPurchase(id);
		if (purchase == null) {
			Log.d("Kinc", id + " purchase id not found in cache");
			return;
		}

		ConsumeParams consumeParams = ConsumeParams.newBuilder()
			.setPurchaseToken(purchase.getPurchaseToken())
			.setDeveloperPayload(purchase.getDeveloperPayload())
			.build();

		billingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
			@Override
			public void onConsumeResponse(BillingResult billingResult, String outToken) {
				final int responseCode = billingResult.getResponseCode();
				KoreActivity.getInstance().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onConsume(responseCode, id);
					}
				});
			}
		});
	}

	public static void acknowledge(final String id) {
		Purchase purchase = findPurchase(id);
		if (purchase == null) {
			Log.d("Kinc", id + " purchase id not found in cache");
			return;
		}

		if (purchase.getPurchaseState() != PurchaseState.PURCHASED) return;
		// Acknowledge the purchase if it hasn't already been acknowledged.
		if (purchase.isAcknowledged()) return;
		AcknowledgePurchaseParams acknowledgePurchaseParams =
		AcknowledgePurchaseParams.newBuilder()
			.setPurchaseToken(purchase.getPurchaseToken())
			.build();
		billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
			@Override
			public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
				final int responseCode = billingResult.getResponseCode();
				KoreActivity.getInstance().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onAcknowledge(responseCode, id);
					}
				});
			}
		});
	}

	static Purchase findPurchase(final String id) {
		Purchase purchase = null;
		for (Purchase cache : PurchasesListener.cacheList) {
			if (cache.getSku().equals(id)) {
				purchase = cache;
				break;
			}
		}
		return purchase;
	}

	static <T> String arrayToJson(List<T> items) {
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
		final int responseCode = billingResult.getResponseCode();
		// Log.d("Kinc", responseCode + " | " + purchases);
		if (purchases != null) {
			for (Purchase purchase : purchases) {
				cacheList.remove(purchase);
				cacheList.add(purchase);
			}
		}
		final String jsonArray = Billing.arrayToJson(purchases);

		KoreActivity.getInstance().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Billing.onPurchase(responseCode, jsonArray);
			}
		});
	}

}
