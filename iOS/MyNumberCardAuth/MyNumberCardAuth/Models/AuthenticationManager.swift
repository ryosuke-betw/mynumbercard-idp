//
//  AuthenticationManager.swift
//  MyNumberCardAuth
//
//  Created by c3lab on 2023/04/24.
//

import CryptoKit
import Foundation
import JOSESwift
import TRETJapanNFCReader_MIFARE_IndividualNumber

public class AuthenticationManager:IndividualNumberReaderSessionDelegate {
    private var authenticationController:AuthenticationController
    private var individualNumberCardSignatureType: IndividualNumberCardSignatureType?
    private var actionURL: String?
    private var reader: IndividualNumberReader!
    
    init(authenticationController: AuthenticationController) {
        self.authenticationController = authenticationController
    }

    public func authenticateForUserVerification(pin: String, nonce: String, actionURL: String) {
        self.actionURL = actionURL
        self.authenticationController.nonce = nonce
        individualNumberCardSignatureType = .userAuthentication
        computeDigitalSignatureForUserVerification(userAuthenticationPIN: pin, dataToSign: nonce)
    }

    public func authenticateForSignature(pin: String, nonce: String, actionURL: String) {
        self.actionURL = actionURL
        self.authenticationController.nonce = nonce
        individualNumberCardSignatureType = .digitalSignature
        computeDigitalCertificateForSignature(signaturePIN: pin, dataToSign: nonce)
    }

    public func individualNumberReaderSession(didRead individualNumberCardData: TRETJapanNFCReader_MIFARE_IndividualNumber.IndividualNumberCardData) {
        switch self.individualNumberCardSignatureType {
        case .userAuthentication:
            if let digitalSignature = individualNumberCardData.computeDigitalSignatureForUserAuthentication,
               let digitalCertificate = individualNumberCardData.userAuthenticationCertificate,
               let actionURL = self.actionURL
            {
                let digitalSignatureBase64URLEncoded = encodingBase64URL(from: digitalSignature)!
                let base64DigitalCertificate = Data(digitalCertificate).base64EncodedString()
                let pemDigitalCertificate = "-----BEGIN CERTIFICATE-----\\n" + base64DigitalCertificate + "\\n-----END CERTIFICATE-----"

                sendVerifySignatureRequest(digitalSignature: digitalSignatureBase64URLEncoded, digitalCertificate: pemDigitalCertificate, actionURL: actionURL)
            }
            break
        case .digitalSignature:
            if let digitalSignature = individualNumberCardData.computeDigitalSignatureForDigitalSignature,
               let digitalCertificate = individualNumberCardData.digitalSignatureCertificate,
               let actionURL = self.actionURL
            {
                let digitalSignatureBase64URLEncoded = encodingBase64URL(from: digitalSignature)!
                let base64DigitalCertificate = Data(digitalCertificate).base64EncodedString()
                let pemDigitalCertificate = "-----BEGIN CERTIFICATE-----\\n" + base64DigitalCertificate + "\\n-----END CERTIFICATE-----"

                sendVerifySignatureRequest(digitalSignature: digitalSignatureBase64URLEncoded, digitalCertificate: pemDigitalCertificate, actionURL: actionURL)
            }
        case .none:
            break
        }
    }

    public func japanNFCReaderSession(didInvalidateWithError _: Error) {}

    private func computeDigitalSignatureForUserVerification(userAuthenticationPIN: String, dataToSign: String) {
        let dataToSignByteArray = [UInt8](dataToSign.utf8)
        reader = IndividualNumberReader(delegate: self)
        // 以下処理はNFC読み取りが非同期で行われ、完了するとindividualNumberReaderSessionが呼び出される
        reader.computeDigitalSignature(signatureType: individualNumberCardSignatureType!,
                                       pin: userAuthenticationPIN,
                                       dataToSign: dataToSignByteArray)
    }

    private func computeDigitalCertificateForSignature(signaturePIN: String, dataToSign: String) {
        let dataToSignByteArray = [UInt8](dataToSign.utf8)
        reader = IndividualNumberReader(delegate: self)
        // 以下処理はNFC読み取りが非同期で行われ、完了するとindividualNumberReaderSessionが呼び出される
        reader.computeDigitalSignature(signatureType: individualNumberCardSignatureType!,
                                       pin: signaturePIN,
                                       dataToSign: dataToSignByteArray)
    }

    private func sendVerifySignatureRequest(digitalSignature: String, digitalCertificate: String, actionURL: String) {
        Task {
            let payload = "{ \"claim\": \"" + digitalCertificate + "\" }"
            let encryptedCertificate = try? await encryptJWE(from: [UInt8](payload.utf8))
            var request = URLRequest(url: URL(string: actionURL)!)

            var mode: String = ""
            switch self.authenticationController.runMode {
            case .Login:
                mode = "login"
            case .Registration:
                mode = "registration"
            case .Replacement:
                mode = "replacement"
            }

            var certificateName: String = ""
            switch self.authenticationController.viewState {
            case .UserVerificationView:
                certificateName = "encryptedUserAuthenticationCertificate"
            case .SignatureView:
                certificateName = "encryptedDigitalSignatureCertificate"
            case .ExplanationView:
                break
            }

            request.httpMethod = "POST"
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            var requestBodyComponents = URLComponents()
            requestBodyComponents.queryItems = [URLQueryItem(name: "mode", value: mode),
                                                URLQueryItem(name: certificateName, value: encryptedCertificate),
                                                URLQueryItem(name: "applicantData", value: self.authenticationController.nonce),
                                                URLQueryItem(name: "sign", value: digitalSignature)]

            request.httpBody = requestBodyComponents.query?.data(using: .utf8)

            let session = HTTPSession(authenticationController: self.authenticationController)
            session.openRedirectURLOnSafari(request: request)
        }
    }
    
    private func encodingBase64URL(from: [UInt8]) -> String? {
        let fromData = Data(from)
        let fromBase64Encoded = fromData.base64EncodedString(options: [])
        let allowedCharacterSet = CharacterSet(charactersIn: "!*'();:@&=+$,/?%#[]").inverted
        let fromBase64URLEncoded = fromBase64Encoded.addingPercentEncoding(withAllowedCharacters: allowedCharacterSet)
        return fromBase64URLEncoded
    }

    private func encryptJWE(from: [UInt8]) async throws -> String {
        if actionURL == nil {
            return ""
        }

        let pattern = /^https?:\/\/[^\/]+\/realms\/[^\/]+/
        let rootUrl = try pattern.firstMatch(in: actionURL!)!.0
        let strUrl = String(rootUrl)
        let jwksUrl = strUrl + "/protocol/openid-connect/certs"

        let request = URLRequest(url: URL(string: jwksUrl)!)
        let (data, _) = try await URLSession.shared.data(for: request)
        let jwks = try JWKSet(data: data)

        for jwk in jwks {
            if jwk["alg"] == "RSA-OAEP-256" {
                let header = JWEHeader(keyManagementAlgorithm: .RSAOAEP256, contentEncryptionAlgorithm: .A128CBCHS256)
                let payload = Payload(Data(from))
                let publicKey: SecKey = try (jwk as! RSAPublicKey).converted(to: SecKey.self)
                let encrypter = Encrypter(keyManagementAlgorithm: .RSAOAEP256, contentEncryptionAlgorithm: .A128CBCHS256, encryptionKey: publicKey)!
                let jwe = try? JWE(header: header, payload: payload, encrypter: encrypter)

                return jwe!.compactSerializedString
            }
        }

        return ""
    }
}
