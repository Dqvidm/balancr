import { useState } from 'react';
import apiClient from '../api/axiosConfig';
import type { TransferRequest } from '../types';

export default function TransferForm({ onTransferComplete }: { onTransferComplete: () => void }) {
    const [fromAccountId, setFromAccountId] = useState('');
    const [toAccountId, setToAccountId] = useState('');
    const [amount, setAmount] = useState('');
    const [error, setError] = useState('');

    const handleTransfer = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        const request: TransferRequest = {
            fromAccountId: parseInt(fromAccountId),
            toAccountId: parseInt(toAccountId),
            amount: parseFloat(amount),
        };

        try {
            await apiClient.post('/ledger/transfer', request);
            setFromAccountId('');
            setToAccountId('');
            setAmount('');
            onTransferComplete();
        } catch (err) {
            console.error('Transfer failed:', err);
            setError('Transfer failed. Please check the account IDs and balance.');
        }
    };

    return (
        <div style={{ border: '1px solid #ccc', padding: '1rem', borderRadius: '4px', marginTop: '1rem' }}>
            <h3>Make a Transfer</h3>
            {error && <p style={{ color: 'red' }}>{error}</p>}
            <form onSubmit={handleTransfer} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '300px' }}>
                <input
                    type="number"
                    placeholder="From Account ID"
                    value={fromAccountId}
                    onChange={(e) => setFromAccountId(e.target.value)}
                    required
                />
                <input
                    type="number"
                    placeholder="To Account ID"
                    value={toAccountId}
                    onChange={(e) => setToAccountId(e.target.value)}
                    required
                />
                <input
                    type="number"
                    placeholder="Amount"
                    step="0.01"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    required
                />
                <button type="submit">Submit Transfer</button>
            </form>
        </div>
    );
}