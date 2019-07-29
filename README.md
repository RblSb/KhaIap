## KhaIap

Google Play billing wrapper for Kha, android-native only, wip.

### Setup

Push these files to `Libraries/iap` for your project dir.
Add `project.addLibrary('iap');` to `khafile.js` and these lines:
```js
// just my optional shortcut, can be removed
const android = project.targetOptions.android_native;
// set paths to folder inside of your project dir (this folder not inside Assets)
// we need to create custom build.gradle file to configure play billing dependency
// `/` as folder separator works on Windows too
android.buildGradlePath = 'data/android/app/build.gradle';
```
Copy this file from `Kha/Kinc/Tools/kincmake/Data/android/app/build.gradle` to your path.
And [change it in this way](https://developer.android.com/google/play/billing/billing_library_overview#Dependencies). At this library documentation time, we need only add these lines at the end of `build.gradle`:
```js
dependencies {
    implementation 'com.android.billingclient:billing:2.0.1'
}
```

### Usage

Example:

```haxe
package;

import kha.System;
// {id:String, name:String, desc:String, price:String, currencyCode:String}
import iap.Iap.Product;
// {id:String}
import iap.Iap.Purchase;
// enum abstract (Ok, Canceled, Error, AlreadyOwned)
import iap.Iap.PurchaseStatus;
import iap.Iap;

class Main {

	static function main() {
		System.start({title: "Empty", width: 800, height: 600}, init);
	}

	static function init(_):Void {
		Iap.init(onInit, onError, onPurchase);
	}

	static function onInit():Void {
		trace("Init complete");
		// request array of products based on array of ids
		// in this case request only market test product
		// you can also test "android.test.canceled" and "android.test.item_unavailable"
		Iap.getProducts(["android.test.purchased"], (arr:Array<Product>) -> {
			trace(arr);
			// we get array of single product, lets request purchase
			// this call will show google billing popup
			Iap.purchase(arr[0].id);
			// also do not call Iap.purchase("myProductId") before getProducts with it.
			// `Iap.purchase` requres products cache with some useless secret info,
			// that we don't need to expose for this cool wrapper
		});
	}

	static function onError():Void {
		trace("Init error");
	}

	static function onPurchase(status:PurchaseStatus, purchase:Null<Purchase>):Void {
		// beware: `purchase` is `null` if status is not Ok!
		trace(status, purchase);
		// so check status first
		switch (status) {
			case Ok:
				trace("Ok");
				switch(purchase.id) {
					case "full_game": // purchase complete
						final unlockFullGame = true;
					case "buy100coins":
						final coins = 100;
						// if item is consumable (can be repurchased) we should call this:
						Iap.consume(purchase.id, onConsume);
				}
			case Canceled: // user cancel purchase
				trace("Canceled");
			case AlreadyOwned:
				// for example user bought "full_game" item on other device for current google account
				// or you forgot to consume some shop item like "buy100coins"
				// in first case you can restore purchase here if you know what id is requested
				// (remember - `purchase` object is `null` here, thanks google)
				// in second case - reset google play market manually, i guess
				trace("AlreadyOwned");
			case Error: // any other status currently
				trace("Error");
		}
	}

	static function onConsume(id:String):Void {
		// `id` item can be repurchased after this callback
		trace('"$id" id consume complete');
	}
}
```

Some other methods:
```haxe
// same as `Iap.init`, but withoud initialization.
// You can call `init` once and just change callbacks after.
Iap.setCallbacks(onComplete, onError, onGetPurchase);
// Removing callbacks
Iap.removeCallbacks();
```

Testing on html5:
- `setCallbacks` always call `onComplete()` callback.
- There is `Iap.sendProducts(code:Int, data:String)` to send data to `getProducts` callback. For example you can do:
```haxe
haxe.Timer.delay(() -> {
	Iap.sendProducts(0, '[{"skuDetailsToken":"","productId":"buy100coins","type":"inapp","price":"notused","price_amount_micros":1990000,"price_currency_code":"USD","title":"","description":""}]');
}, 1500);
```

### TODO

- `getPurchase(id:String, callback()->Void)` or something to restore purchases in a better way
- Maybe iOS support someday with a lot of breaking changes
