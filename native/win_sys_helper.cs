using System;
using System.Security.Cryptography.X509Certificates;
using System.IO;

namespace CyvoraX.Native
{
    /// <summary>
    /// CyvoraX Suite - C# / .NET Windows System Helper
    /// Interacts directly with Windows Certificate Store for dynamic Root CA installation.
    /// </summary>
    public class WinSysHelper
    {
        public static bool InstallRootCertificate(string certPath)
        {
            try
            {
                if (!File.Exists(certPath))
                    return false;

                X509Certificate2 cert = new X509Certificate2(certPath);
                using (X509Store store = new X509Store(StoreName.Root, StoreLocation.CurrentUser))
                {
                    store.Open(OpenFlags.ReadWrite);
                    store.Add(cert);
                    store.Close();
                }
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine("[WinSysHelper Error] Failed to install CA: " + ex.Message);
                return false;
            }
        }
    }
}
