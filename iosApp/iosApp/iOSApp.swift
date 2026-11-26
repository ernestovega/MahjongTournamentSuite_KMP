import Shared
import SwiftUI

/// The iOS app of the suite. The screens are the shared Compose code of the `suite` module, like the Android app.
/// See docs/ios-release.md.
@main
struct IOSApp: App {
    init() {
        FirebaseSetup.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // A link of the site that opens the app: the QR of a table, a tournament, a registration...
                .onOpenURL(perform: openLink)
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL { openLink(url) }
                }
        }
    }

    /// The app shows the link. A page of the site that is not the app (the privacy policy...) opens in Safari.
    private func openLink(_ url: URL) {
        if !IosSuite.shared.openUrl(url: url.absoluteString) {
            UIApplication.shared.open(url)
        }
    }
}
