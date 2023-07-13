//
//  NFCReadingView.swift
//  MyNumberCardAuth
//
//  Created by abelstaff on 2023/04/20.
//

import SwiftUI
import Combine

struct NFCReadingView: View {
    @Binding var queryDict : [String: String]?
    @ObservedObject var authenticationController:AuthenticationController
    @FocusState private var isActive:Bool
    @ObservedObject var controller:ViewController
    
    var body: some View {
        VStack{
            Text(NSLocalizedString("common01-001", comment: "マイナンバーカード認証"))
                .font(.title)
                .multilineTextAlignment(.center)
                .bold()
                .padding(/*@START_MENU_TOKEN@*/.all/*@END_MENU_TOKEN@*/)
            
            (Text(NSLocalizedString("m01-001", comment: "マイナンバーカードの")) + Text(NSLocalizedString("m01-002", comment: "利用者証明用電子証明書")).fontWeight(.bold) + Text(NSLocalizedString("m01-003", comment: "のパスワード（4桁の数字）を入力し、読み取り開始ボタンを押す"))
                ).font(.title3)
                .multilineTextAlignment(.center)
                .padding(/*@START_MENU_TOKEN@*/.all/*@END_MENU_TOKEN@*/)
                .frame(height: 140.0)
            
            SecureField(NSLocalizedString("common01-003", comment: "暗証番号"), text: $controller.inputPIN)
                .padding(.all)
                .frame( height: 80.0)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .keyboardType(.numberPad)
                .focused(self.$isActive)
                .multilineTextAlignment(.center)
                .toolbar {
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button(NSLocalizedString("common01-004", comment: "閉じる")) {
                            isActive = false
                        }
                    }
                }.onReceive(Just(controller.inputPIN)) { _ in
                    //最大文字数を超えたら、最大文字数までの文字列を代入する
                    if controller.inputPIN.count > 4 {
                        controller.inputPIN = String(controller.inputPIN.prefix(4))
                    }
                }
            
            Button(NSLocalizedString("common01-002", comment: "読み取り開始")) {
                if controller.isEnableButton() {
                    self.authenticationController.readStart(pin: controller.inputPIN, nonce: controller.nonce ,actionURL: controller.actionURL)
                }
            }.modifier(SmallButtonModifier(color: controller.toggleColor()))
        }
        .onAppear{
            if(self.controller.actionURL.isEmpty || self.controller.nonce.isEmpty){
                // アプリ単独起動時
                self.authenticationController.viewState = .ExplanationView
            }
        }
        .alert(isPresented:self.$authenticationController.isAlert){
            Alert(title:Text(self.authenticationController.messageTitle),message:Text(self.authenticationController.messageString),dismissButton: .default(Text("OK"),action: {
                if(self.authenticationController.isErrorOpenURL == true){
                    if (self.authenticationController.openURL.isEmpty == false)
                    {
                        if let openURL = URL(string:self.authenticationController.openURL){
                            UIApplication.shared.open(openURL)
                        };
                    }
                }
            }))
        }
    }
}

struct NFCReadingView_Previews: PreviewProvider {
    @ObservedObject static var authenticationController:AuthenticationController = AuthenticationController()
    @ObservedObject static var controlller:ViewController = ViewController()
    static var previews: some View {
        NFCReadingView(queryDict: .constant([:]),authenticationController: authenticationController,controller: controlller)
    }
}
