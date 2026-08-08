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
        public static bool InstallRootCertificate(string certPath, string password = null)
        {
            try
            {
                if (!File.Exists(certPath))
                    return false;

                X509Certificate2 cert = string.IsNullOrEmpty(password)
                    ? new X509Certificate2(certPath)
                    : new X509Certificate2(certPath, password);

                try
                {
                    using (X509Store store = new X509Store(StoreName.Root, StoreLocation.CurrentUser))
                    {
                        store.Open(OpenFlags.ReadWrite);
                        store.Add(cert);
                        store.Close();
                    }
                    return true;
                }
                finally
                {
                    cert.Dispose();
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine("[WinSysHelper Error] Failed to install CA: " + ex.Message);
                return false;
            }
        }

        public static bool RemoveRootCertificate(string thumbprint)
        {
            try
            {
                using (X509Store store = new X509Store(StoreName.Root, StoreLocation.CurrentUser))
                {
                    store.Open(OpenFlags.ReadWrite);
                    X509Certificate2Collection certs = store.Certificates.Find(
                        X509FindType.FindByThumbprint, thumbprint, false);
                    if (certs.Count > 0)
                    {
                        store.Remove(certs[0]);
                    }
                    store.Close();
                }
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine("[WinSysHelper Error] Failed to remove CA: " + ex.Message);
                return false;
            }
        }
    }
}
