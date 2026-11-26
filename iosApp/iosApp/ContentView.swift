import Shared
import SwiftUI
import UIKit

/// The Compose screens of the suite. Compose draws under the bars of the system and handles the safe areas itself.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(url: nil)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
