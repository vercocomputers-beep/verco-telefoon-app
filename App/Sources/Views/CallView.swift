import SwiftUI

/// Scherm tijdens een gesprek (inkomend, uitgaand, verbonden).
struct CallView: View {
    @EnvironmentObject var phone: PhoneManager
    @State private var showKeypad = false

    var body: some View {
        ZStack {
            Theme.navy.ignoresSafeArea()
            VStack(spacing: 18) {
                Spacer().frame(height: 40)
                Text(phone.remoteName.isEmpty ? phone.remoteNumber : phone.remoteName)
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                if !phone.remoteName.isEmpty {
                    Text(phone.remoteNumber).foregroundStyle(.white.opacity(0.7))
                }
                stateText
                    .foregroundStyle(.white.opacity(0.8))
                Spacer()

                if phone.phase == .active {
                    HStack(spacing: 28) {
                        RoundButton(icon: phone.muted ? "mic.slash.fill" : "mic.fill", label: "Stil", on: phone.muted) {
                            phone.toggleMute()
                        }
                        RoundButton(icon: "circle.grid.3x3.fill", label: "Toetsen", on: showKeypad) {
                            showKeypad.toggle()
                        }
                        RoundButton(icon: "speaker.wave.2.fill", label: "Luidspreker", on: phone.speaker) {
                            phone.toggleSpeaker()
                        }
                    }
                    if showKeypad {
                        InCallKeypad { phone.sendDtmf($0) }
                    }
                }

                HStack(spacing: 60) {
                    if phone.phase == .incoming {
                        CallActionButton(icon: "phone.fill", color: Theme.green) { phone.accept() }
                    }
                    if phone.phase != .ended {
                        CallActionButton(icon: "phone.down.fill", color: Theme.red) { phone.hangup() }
                    }
                }
                .padding(.bottom, 40)
            }
            .padding()
        }
    }

    @ViewBuilder private var stateText: some View {
        switch phone.phase {
        case .incoming: Text("Belt je…")
        case .outgoing: Text("Bellen…")
        case .ringing: Text("Gaat over…")
        case .active:
            if let start = phone.connectedAt {
                Text(start, style: .timer).monospacedDigit()
            } else {
                Text("Verbonden")
            }
        case .ended: Text("Gesprek beëindigd")
        case .none: Text("")
        }
    }
}

struct RoundButton: View {
    let icon: String
    let label: String
    let on: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.title2)
                    .frame(width: 66, height: 66)
                    .background(Circle().fill(on ? Color.white : Color.white.opacity(0.18)))
                    .foregroundStyle(on ? Theme.navy : .white)
                Text(label).font(.caption).foregroundStyle(.white)
            }
        }
    }
}

struct CallActionButton: View {
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.title)
                .foregroundStyle(.white)
                .frame(width: 76, height: 76)
                .background(Circle().fill(color))
        }
    }
}

struct InCallKeypad: View {
    let onKey: (Character) -> Void
    private let rows = ["123", "456", "789", "*0#"]

    var body: some View {
        VStack(spacing: 10) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 18) {
                    ForEach(Array(row), id: \.self) { c in
                        Button {
                            onKey(c)
                        } label: {
                            Text(String(c))
                                .font(.title2)
                                .foregroundStyle(.white)
                                .frame(width: 56, height: 56)
                                .background(Circle().fill(Color.white.opacity(0.15)))
                        }
                    }
                }
            }
        }
    }
}
