package iap;

import cpp.Callable;
import haxe.Json;
import haxe.Timer;

typedef Product = {
	id:String,
	name:String,
	desc:String,
	price:String,
	currencyCode:String
}

typedef Purchase = {
	id:String
}

enum abstract PurchaseStatus(Int) {
	var Ok;
	var Canceled;
	var Error;
	var AlreadyOwned;
}

private typedef SkuDetails = {
	skuDetailsToken:String,
	productId:String,
	type:String,
	price:String,
	price_amount_micros:String,
	price_currency_code:String,
	title:String,
	description:String
}

private typedef PurchaseDetails = {
	packageName:String,
	acknowledged:Bool,
	orderId:String,
	productId:String,
	developerPayload:String,
	purchaseTime:Int,
	purchaseState:Int,
	purchaseToken:String
}

typedef GetProductsFunc = (products:Array<Product>)->Void;
typedef GetPurchasesFunc = (purchases:Array<Purchase>)->Void;
typedef PurchaseFunc = (status:PurchaseStatus, purchase:Purchase)->Void;
typedef ConsumeFunc = (id:String)->Void;
typedef OnError = (status:Int)->Void;

#if !cpp
class Iap {
	static var onGetProducts:GetProductsFunc;

	public static function init(onComplete:()->Void, onError:()->Void, onGetPurchase:PurchaseFunc):Void {}
	public static function setCallbacks(onComplete:()->Void, onError:()->Void, onGetPurchase:PurchaseFunc):Void {
		onComplete();
	}
	public static function removeCallbacks():Void {}
	public static function getProducts(ids:Array<String>, callback:GetProductsFunc):Void {
		onGetProducts = callback;
	}
	public static function sendProducts(code:Int, data:String):Void {
		_onGetProducts(code, data);
	}
	public static function getPurchases(onGet:GetPurchasesFunc, onError:OnError):Void {}
	public static function purchase(id:String):Void {}
	public static function consume(id:String, callback:ConsumeFunc):Void {}
	public static function acknowledge(id:String, callback:ConsumeFunc):Void {}
	static function _onGetProducts(status:Int, data:String):Void {
		final skus:Array<SkuDetails> = Helper.parseSkuDetails(data);
		final arr:Array<Product> = [];
		for (sku in skus) {
			arr.push({
				id: sku.productId,
				price: Helper.parseMicro(sku.price_amount_micros),
				name: sku.title,
				desc: sku.description,
				currencyCode: sku.price_currency_code
			});
		}
		onGetProducts(arr);
	}
}
#else
class Iap {

	static var onInitComplete:()->Void;
	static var onInitError:()->Void;
	static var onGetProducts:GetProductsFunc;
	static var onGetPurchases:GetPurchasesFunc;
	static var onGetPurchasesError:OnError;
	static var onPurchase:PurchaseFunc;
	static var onConsume:ConsumeFunc;
	static var onAcknowledge:ConsumeFunc;

	public static function init(onComplete:()->Void, onError:()->Void, onGetPurchase:PurchaseFunc):Void {
		onInitComplete = onComplete;
		onInitError = onError;
		onPurchase = onGetPurchase;
		final complete = Callable.fromStaticFunction(_onInitComplete);
		final error = Callable.fromStaticFunction(_onInitError);
		final purchase = Callable.fromStaticFunction(_onPurchase);
		Billing.init(complete, error, purchase);
	}

	public static function setCallbacks(onComplete:()->Void, onError:()->Void, onGetPurchase:PurchaseFunc):Void {
		onInitComplete = onComplete;
		onInitError = onError;
		onPurchase = onGetPurchase;
		final complete = Callable.fromStaticFunction(_onInitComplete);
		final error = Callable.fromStaticFunction(_onInitError);
		final purchase = Callable.fromStaticFunction(_onPurchase);
		Billing.setCallbacks(complete, error, purchase);
	}

	public static function removeCallbacks():Void {
		onInitComplete = _emptyCallback;
		onInitError = _emptyCallback;
		onPurchase = _emptyPurchase;
		final complete = Callable.fromStaticFunction(_onInitComplete);
		final error = Callable.fromStaticFunction(_onInitError);
		final purchase = Callable.fromStaticFunction(_onPurchase);
		Billing.setCallbacks(complete, error, purchase);
	}

	public static function getProducts(ids:Array<String>, callback:GetProductsFunc):Void {
		onGetProducts = callback;
		final func = Callable.fromStaticFunction(_onGetProducts);
		Billing.getProducts(ids.join(","), func);
	}

	public static function getPurchases(onGet:GetPurchasesFunc, onError:OnError):Void {
		onGetPurchases = onGet;
		onGetPurchasesError = onError;
		final func = Callable.fromStaticFunction(_onGetPurchases);
		Billing.getPurchases(func);
	}

	public static function purchase(id:String):Void {
		Billing.purchase(id);
	}

	public static function consume(id:String, callback:ConsumeFunc):Void {
		onConsume = callback;
		final func = Callable.fromStaticFunction(_onConsume);
		Billing.consume(id, func);
	}

	public static function acknowledge(id:String, callback:ConsumeFunc):Void {
		onAcknowledge = callback;
		final func = Callable.fromStaticFunction(_onAcknowledge);
		Billing.acknowledge(id, func);
	}

	static function _onInitComplete():Void {
		Timer.delay(() -> onInitComplete(), 50);
	}

	static function _onInitError():Void {
		Timer.delay(() -> onInitError(), 50);
	}

	static function _onGetProducts(status:Int, data:String):Void {
		trace(status, data);
		if (status != 0) {
			Timer.delay(() -> onInitError(), 50);
			return;
		}
		final skus:Array<SkuDetails> = Helper.parseSkuDetails(data);
		final arr:Array<Product> = [];
		for (sku in skus) {
			arr.push({
				id: sku.productId,
				price: Helper.parseMicro(sku.price_amount_micros),
				name: sku.title,
				desc: sku.description,
				currencyCode: sku.price_currency_code
			});
		}
		Timer.delay(() -> onGetProducts(arr), 50);
	}

	static function _onGetPurchases(status:Int, data:String):Void {
		trace(status, data);
		if (status != 0) {
			Timer.delay(() -> onGetPurchasesError(status), 50);
			return;
		}
		final purchases:Array<PurchaseDetails> = Json.parse(data);
		final arr:Array<Purchase> = [];
		for (purchase in purchases) {
			arr.push({
				id: purchase.productId
			});
		}
		Timer.delay(() -> onGetPurchases(arr), 50);
	}

	static function _onPurchase(code:Int, data:String):Void {
		trace(code, data);
		final purchases:Array<PurchaseDetails> = Json.parse(data);
		final status:PurchaseStatus = switch (code) {
			case 0: Ok;
			case 1: Canceled;
			case 7: AlreadyOwned;
			default: Error;
		}

		for (purchase in purchases) {
			Timer.delay(() -> {
				onPurchase(status, {
					id: purchase.productId
				});
			}, 50);
		}
		if (purchases.length == 0) Timer.delay(() -> {
			onPurchase(status, null);
		}, 50);
	}

	static function _onConsume(status:Int, data:String):Void {
		trace(status, data);
		Timer.delay(() -> onConsume(data), 50);
	}

	static function _onAcknowledge(status:Int, data:String):Void {
		trace(status, data);
		Timer.delay(() -> onAcknowledge(data), 50);
	}

	static function _emptyCallback():Void {}
	static function _emptyPurchase(status:PurchaseStatus, purchase:Purchase):Void {}

}

private typedef CppVoid = Callable<()->Void>;
private typedef CppStatusData = Callable<(status:Int, data:String)->Void>;
private typedef CppStatusId = Callable<(status:Int, id:String)->Void>;

@:include("Billing.h")
@:native("Billing")
@:unreflective
@:structAccess
extern class Billing {
	@:native("Billing::init")
	static function init(complete:CppVoid, error:CppVoid, purchase:CppStatusData):Void;
	@:native("Billing::setCallbacks")
	static function setCallbacks(complete:CppVoid, error:CppVoid, purchase:CppStatusData):Void;
	@:native("Billing::getProducts")
	static function getProducts(ids:String, fn:CppStatusData):Void;
	@:native("Billing::getPurchases")
	static function getPurchases(fn:CppStatusData):Void;
	@:native("Billing::purchase")
	static function purchase(id:String):Void;
	@:native("Billing::consume")
	static function consume(id:String, fn:CppStatusId):Void;
	@:native("Billing::acknowledge")
	static function acknowledge(id:String, fn:CppStatusId):Void;
}
#end

private class Helper {

	public static function parseSkuDetails(data:String):Array<SkuDetails> {
		// replacing Int64 to String in json data
		data = ~/(price_amount_micros":)[ \t\n]*([0-9]+)/g.replace(data, '$1"$2"');
		return Json.parse(data);
	}

	public static function parseMicro(m:String):String {
		final buf = new StringBuf();
		var max = 4;
		for (i in 0...7 - m.length) m = "0" + m;
		final off = 3 - m.length % 3;
		for (i in 0...max) {
			final char = m.charAt(i);
			// remove latest zero if micro smaller 10,00
			if (i == max - 1 && m.length < 2 + 6 && char == "0") break;
			buf.add(char);
			if (i == max - 1) break;
			if ((off + i) % 3 == 2) buf.add(",");
		}
		return buf.toString();
	}

}
