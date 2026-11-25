import Foundation
import Shared

#if canImport(FirebaseCore)
import FirebaseAnalytics
import FirebaseAppCheck
import FirebaseCore
#endif

/// Firebase App Check (App Attest) and Analytics, which only exist as Swift SDKs. They hand themselves to the shared
/// code through `IosSuite`. Without a `GoogleService-Info.plist` for the configuration (iosApp/Firebase), the app
/// works without them: it sends no App Check token and no Analytics. See docs/ios-release.md.
enum FirebaseSetup {
    static func configure() {
        #if canImport(FirebaseCore)
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        let debug = (Bundle.main.object(forInfoDictionaryKey: "MTSEnvironment") as? String) == "debug"
        // The provider goes in before configure(), as Firebase asks.
        if debug {
            AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        } else {
            AppCheck.setAppCheckProviderFactory(MtsAppCheckProviderFactory())
        }
        FirebaseApp.configure()
        IosSuite.shared.configureAppCheck(provider: FirebaseAppCheckBridge(), debug: debug)
        IosSuite.shared.configureAnalytics(analytics: FirebaseAnalyticsBridge())
        #endif
    }
}

#if canImport(FirebaseCore)
/// App Attest where the device has it, and DeviceCheck on older devices.
private final class MtsAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        if #available(iOS 14.0, *) {
            return AppAttestProvider(app: app)
        }
        return DeviceCheckProvider(app: app)
    }
}

private final class FirebaseAppCheckBridge: NSObject, MtsAppCheck {
    func token(completion: @escaping (String?, String?) -> Void) {
        AppCheck.appCheck().token(forcingRefresh: false) { token, error in
            if let token {
                completion(token.token, nil)
            } else {
                completion(nil, error.map { String(describing: type(of: $0)) + ":" + $0.localizedDescription } ?? "unknown")
            }
        }
    }
}

private final class FirebaseAnalyticsBridge: NSObject, MtsAnalytics {
    func setCollectionEnabled(enabled: Bool) {
        Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    func logEvent(name: String, params: [String: Any]) {
        Analytics.logEvent(name, parameters: params)
    }

    func setUserProperty(name: String, value: String?) {
        Analytics.setUserProperty(value, forName: name)
    }
}
#endif
