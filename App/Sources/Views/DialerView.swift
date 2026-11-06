import SwiftUI

/// Kiesscherm: nummer intikken en bellen.
struct DialerView: View {
    @EnvironmentObject var phone: PhoneManager
    @State private var number = ""

    private let keys: [[(String, String)]] = [
        [("1", ""), ("2", "ABC"), ("3", "DEF")],
        [("4", "GHI"), ("5", "JKL"), ("6", "MNO")],
        [("7", "PQRS"), ("8", "TUV"), ("9", "WXYZ")],
        [("*", ""), ("0", "+"), ("#", "")],
    ]

    var body: some View {
        VStack(spacing: 0) {
            StatusHeader()
            Spacer()
            Text(number.isEmpty ? " " : number)
                .font(.system(size: 38, weight: .light, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.horizontal)
            Spacer().frame(height: 24)
            VStack(spacing: 14) {
                ForEach(0..<keys.count, id: \.self) { r in
                    HStack(spacing: 22) {
                        ForEach(keys[r], id: \.0) { key in
                            KeyButton(digit: key.0, letters: key.1) { number.append(key.0) }
                        }
                    }
                }
            }
            HStack(spacing: 22) {
                Color.clear.frame(width: 76, height: 76)
                Button {
                    phone.call(number)
                } label: {
                    Image(systemName: "phone.fill")
                        .font(.title)
                        .foregroundStyle(.white)
                        .frame(width: 76, height: 76)
                        .background(Circle().fill(Theme.green))
                }
                .disabled(number.isEmpty || phone.status != .registered)
                Button {
                    if !number.isEmpty { number.removeLast() }
                } label: {
                    Image(systemName: "delete.left")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                        .frame(width: 76, height: 76)
                }
                .opacity(number.isEmpty ? 0 : 1)
            }
            .padding(.top, 14)
            Spacer()
        }
    }
}

struct KeyButton: View {
    let digit: String
    let letters: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                Text(digit).font(.system(size: 32, weight: .regular, design: .rounded))
                Text(letters).font(.system(size: 10, weight: .semibold)).foregroundStyle(.secondary)
            }
            .frame(width: 76, height: 76)
            .background(Circle().fill(Color(.secondarySystemBackground)))
        }
        .buttonStyle(.plain)
    }
}
