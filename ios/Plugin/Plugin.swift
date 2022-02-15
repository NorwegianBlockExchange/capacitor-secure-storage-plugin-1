import Foundation
import Capacitor
import LocalAuthentication

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(SecureStoragePlugin)
public class SecureStoragePlugin: CAPPlugin {
    
    private func fail(call: CAPPluginCall, template: String, status: OSStatus? = nil) {
        if (status != nil) { call.reject(String(format: template, status!)) }
        else { call.reject(template) }
    }
    
    private func removeFromKeychain(key: String) -> OSStatus {
        // Create a query dict for deleting our keychain item
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        
        // execute the query, return the status
        return SecItemDelete(query as CFDictionary)
    }
    
    private func getBiometricAccessControl() -> SecAccessControl {
        var accessControl: SecAccessControl?
        var error: Unmanaged<CFError>?
        
        // Create access control rules for new keychain item
        accessControl = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, // requires device to have passcode, becomes inaccessible if passcode removed
            .biometryCurrentSet, // requires biometric auth, is invalidated if the user's biometry changes (re-enrol faceID, remove/add fingers to TouchID)
            &error)
        
        precondition(accessControl != nil, "SecAccessControlCreateWithFlags failed")
        return accessControl!
    }

    @objc func set(_ call: CAPPluginCall) {
        let key = call.getString("key") ?? ""
        let value = call.getString("value") ?? ""
        let encoded = value.data(using: String.Encoding.utf8)!
        
        print(String(format: "Secure-Storage: storing key %@ with value %@", key, value))
        
        let accessControl = getBiometricAccessControl()
        
//        let context = LAContext()
//        context.touchIDAuthenticationAllowableReuseDuration = 300 // biometric lifetime of 5 mim

        // Create a query dict for executing the add to keychain
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrAccessControl as String: accessControl,
//            kSecUseAuthenticationContext as String: context,
            kSecValueData as String: encoded
        ]
        
        // Execute the query & catch errors
        var status = SecItemAdd(query as CFDictionary, nil)
        
        // If item already exists, remove & set new value
        if (status == errSecDuplicateItem) {
            print("errSecDuplicateItem")
            let deleteStatus = removeFromKeychain(key: key)
            
            guard deleteStatus == errSecSuccess else {
                fail(call: call, template: "Failed to set value securely, error code: %d", status: deleteStatus)
                return
            }
            
            status = SecItemAdd(query as CFDictionary, nil)
        }
        
        guard status == errSecSuccess else {
            fail(call: call, template: "Failed to set value securely, error code: %d", status: status)
            return
        }
        
        call.resolve(["value": true])
    }
    
    @objc func get(_ call: CAPPluginCall) {
        let key = call.getString("key") ?? ""
        let prompt = call.getString("prompt") ?? ""
        
        print(String(format: "Secure-Storage: getting key %@", key))
        
        // Create a query dict for fetching our keychain item, including biometric prompt
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnAttributes as String: true,
            kSecUseOperationPrompt as String: prompt,
            kSecReturnData as String: true
        ]
        
        // Execute query, assign result to pointer, catch errors
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else {
            fail(call: call, template: "Failed to get value securely, error code: %d", status: status)
            return
        }
        
        
        // Extract string from result data, throw error if its not a string
        guard let resultData = result as? [String : Any],
            let secretData = resultData[kSecValueData as String] as? Data,
            let secret = String(data: secretData, encoding: String.Encoding.utf8)
        else {
            fail(call: call, template: "Failed to parse stored value")
            return
        }
        
        call.resolve([
            "value": secret
        ])
    }
    
    @objc func remove(_ call: CAPPluginCall) {
        let key = call.getString("key") ?? ""
        
        print(String(format: "Secure-Storage: removing key %@", key))
    
        let status = removeFromKeychain(key: key)
        guard status == errSecSuccess else {
            fail(call: call, template: "Failed to remove value securely, error code %d", status: status)
            return
        }
        
        call.resolve(["value": true])
    }
    
    @objc func getPlatform(_ call: CAPPluginCall) {
        call.resolve(["value": "ios"])
    }
}
