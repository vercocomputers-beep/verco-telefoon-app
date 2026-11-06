import SwiftUI

struct RootView: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager

    var body: some View {
        Group {
            if store.config == nil {
                SetupView()
            } else {
                MainTabs()
            }
        }
        .fullScreenCover(isPresented: Binding(get: { phone.phase != .none }, set: { _ in })) {
            CallView()
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager
    @ObservedObject private var recents = RecentStore.shared
    @StateObject private var phonebook = Phonebook()

    var body: some View {
        TabView {
            DialerView()
                .tabItem { Label("Kiezen", systemImage: "circle.grid.3x3.fill") }
            RecentsView()
                .tabItem { Label("Recent", systemImage: "clock.fill") }
                .badge(recents.unseenMissed)
            ContactsView(phonebook: phonebook)
                .tabItem { Label("Contacten", systemImage: "person.2.fill") }
            FunctionsView()
                .tabItem { Label("Functies", systemImage: "star.fill") }
                .badge(phone.voicemailNew)
            SettingsView()
                .tabItem { Label("Instellingen", systemImage: "gearshape.fill") }
        }
        .task {
            await store.refresh()          // instellingen stil verversen bij de centrale
            if let url = store.config?.phonebook { await phonebook.load(from: url) }
        }
    }
}

/// Bovenbalk met naam, toestelnummer en status van de aanmelding.
struct StatusHeader: View {
    @EnvironmentObject var store: AccountStore
    @EnvironmentObject var phone: PhoneManager

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(color)
                .frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 1) {
                Text(store.config.map { "\($0.user.name) · \($0.user.ext)" } ?? "")
                    .font(.subheadline.weight(.semibold))
                Text(phone.status.text)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    private var color: Color {
        switch phone.status {
        case .registered: return Theme.green
        case .connecting: return .orange
        case .failed: return Theme.red
        case .off: return .gray
        }
    }
}
