import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/axiosConfig';
import type { Account } from '../types';
import TransferForm from '../components/TransferForm';
import TransactionHistory from '../components/TransactionHistory';
import CreateAccountForm from '../components/CreateAccountForm';

export default function Dashboard() {
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [refreshTrigger, setRefreshTrigger] = useState(0);
    const navigate = useNavigate();

    const fetchData = async () => {
        try {
            const response = await apiClient.get('/accounts');
            setAccounts(response.data);
        } catch (error) {
            console.error('Failed to fetch accounts:', error);
        }
    };

    useEffect(() => {
        fetchData();
    }, [refreshTrigger]);

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    const handleDataChange = () => {
        setRefreshTrigger(prev => prev + 1);
    };

    return (
        <div style={{ padding: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2>Dashboard</h2>
                <button onClick={handleLogout}>Logout</button>
            </div>

            <div style={{ display: 'flex', gap: '2rem', marginTop: '2rem', flexWrap: 'wrap' }}>
                <div style={{ flex: '1 1 300px' }}>
                    <h3>Your Accounts</h3>
                    {accounts.length === 0 ? (
                        <p>No accounts found.</p>
                    ) : (
                        <ul style={{ listStyle: 'none', padding: 0 }}>
                            {accounts.map((account) => (
                                <li
                                    key={account.id}
                                    style={{ border: '1px solid #ccc', padding: '1rem', marginBottom: '1rem', borderRadius: '4px' }}
                                >
                                    <strong>Account ID:</strong> {account.id} <br />
                                    <strong>Type:</strong> {account.accountType} <br />
                                    <strong>Balance:</strong> ${account.balance?.toFixed(2) || '0.00'}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                <div style={{ flex: '1 1 300px', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    <CreateAccountForm onAccountCreated={handleDataChange} />
                    <TransferForm onTransferComplete={handleDataChange} />
                </div>
            </div>

            <TransactionHistory refreshTrigger={refreshTrigger} />
        </div>
    );
}