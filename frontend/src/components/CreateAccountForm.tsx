import { useState } from 'react';
import apiClient from '../api/axiosConfig';
import type { AccountCreateRequest } from '../types';

export default function CreateAccountForm({ onAccountCreated }: { onAccountCreated: () => void }) {
    const [accountType, setAccountType] = useState('ASSET');
    const [initialBalance, setInitialBalance] = useState('');
    const [error, setError] = useState('');

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        const request: AccountCreateRequest = {
            accountType,
            initialBalance: parseFloat(initialBalance) || 0,
        };

        try {
            await apiClient.post('/accounts', request);
            setInitialBalance('');
            onAccountCreated();
        } catch (err) {
            console.error('Account creation failed:', err);
            setError('Failed to create account.');
        }
    };

    return (
        <div style={{ border: '1px solid #ccc', padding: '1rem', borderRadius: '4px', marginTop: '1rem' }}>
            <h3>Create Account</h3>
            {error && <p style={{ color: 'red' }}>{error}</p>}
            <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '300px' }}>
                <select value={accountType} onChange={(e) => setAccountType(e.target.value)} required>
                    <option value="ASSET">Asset</option>
                    <option value="LIABILITY">Liability</option>
                    <option value="EQUITY">Equity</option>
                    <option value="REVENUE">Revenue</option>
                    <option value="EXPENSE">Expense</option>
                </select>
                <input
                    type="number"
                    placeholder="Initial Balance"
                    step="0.01"
                    value={initialBalance}
                    onChange={(e) => setInitialBalance(e.target.value)}
                    required
                />
                <button type="submit">Create Account</button>
            </form>
        </div>
    );
}